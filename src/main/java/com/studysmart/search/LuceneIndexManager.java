package com.studysmart.search;

import com.studysmart.config.StudySmartProperties;
import com.studysmart.domain.Chunk;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One Lucene index per project, ranked with BM25. Keeping a physically
 * separate index per project (rather than one shared index filtered by a
 * projectId field) means a course's material can never leak into another
 * course's search results even if a query is built wrong somewhere upstream.
 */
@Component
public class LuceneIndexManager {

    private static final Logger log = LoggerFactory.getLogger(LuceneIndexManager.class);
    private static final String FIELD_ID = "id";
    private static final String FIELD_DOCUMENT_ID = "documentId";
    private static final String FIELD_CONTENT = "content";

    /**
     * Bumped whenever the text analysis changes. An index written by an
     * older version has its terms stored differently (unstemmed, stopwords
     * kept), so queries built the new way would silently under-match it -
     * {@link #isStale} reports that and the index is rebuilt from the chunks
     * in SQLite rather than quietly returning worse results.
     */
    static final String ANALYZER_VERSION = "english-v1";
    private static final String VERSION_FILE = ".analyzer-version";

    /**
     * English analysis: folds case, drops stopwords ("where", "does",
     * "the") so a question's grammar doesn't outvote its subject, and stems
     * ("cells" matches "cell", "produces" matches "producing").
     */
    static Analyzer analyzer() {
        return new EnglishAnalyzer();
    }

    private final StudySmartProperties properties;
    private final Map<String, Object> projectLocks = new ConcurrentHashMap<>();

    public LuceneIndexManager(StudySmartProperties properties) {
        this.properties = properties;
    }

    private Path indexPath(String projectId) {
        return properties.luceneDir().resolve(projectId);
    }

    private Object lockFor(String projectId) {
        return projectLocks.computeIfAbsent(projectId, k -> new Object());
    }

    public void indexChunks(String projectId, List<Chunk> chunks) throws IOException {
        if (chunks.isEmpty()) {
            return;
        }
        synchronized (lockFor(projectId)) {
            Path path = indexPath(projectId);
            Files.createDirectories(path);
            try (Analyzer analyzer = analyzer();
                 FSDirectory dir = FSDirectory.open(path);
                 IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
                for (Chunk chunk : chunks) {
                    Document doc = new Document();
                    doc.add(new StringField(FIELD_ID, chunk.id(), Field.Store.YES));
                    doc.add(new StringField(FIELD_DOCUMENT_ID, chunk.documentId(), Field.Store.YES));
                    doc.add(new StoredField("ordinal", chunk.ordinal()));
                    doc.add(new TextField(FIELD_CONTENT, chunk.content(), Field.Store.NO));
                    writer.addDocument(doc);
                }
                writer.commit();
            }
            Files.writeString(path.resolve(VERSION_FILE), ANALYZER_VERSION);
        }
    }

    /** True when this project has an index that an older analyzer wrote, so it must be rebuilt before it can be trusted. */
    public boolean isStale(String projectId) {
        Path path = indexPath(projectId);
        if (!Files.isDirectory(path) || !hasIndexFiles(path)) {
            return false;
        }
        try {
            Path marker = path.resolve(VERSION_FILE);
            return !Files.exists(marker) || !ANALYZER_VERSION.equals(Files.readString(marker).trim());
        } catch (IOException e) {
            return true;
        }
    }

    public void deleteDocument(String projectId, String documentId) throws IOException {
        synchronized (lockFor(projectId)) {
            Path path = indexPath(projectId);
            if (!Files.isDirectory(path)) {
                return;
            }
            try (Analyzer analyzer = analyzer();
                 FSDirectory dir = FSDirectory.open(path);
                 IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
                writer.deleteDocuments(new Term(FIELD_DOCUMENT_ID, documentId));
                writer.commit();
            }
        }
    }

    public void deleteProject(String projectId) {
        synchronized (lockFor(projectId)) {
            Path path = indexPath(projectId);
            try {
                if (Files.isDirectory(path)) {
                    try (var walk = Files.walk(path)) {
                        walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
                    }
                }
            } catch (IOException | UncheckedIOException e) {
                log.warn("Failed to delete Lucene index for project {}", projectId, e);
            }
            // The lock entry stays: dropping it here would let a thread waiting
            // on this project take a brand-new lock object and index into the
            // directory being deleted. One small map entry per project is a
            // cheaper price than that race.
        }
    }

    public List<SearchHit> search(String projectId, String queryText, int topK) throws IOException {
        Path path = indexPath(projectId);
        if (!hasIndexFiles(path)) {
            return List.of();
        }

        try (Analyzer analyzer = analyzer();
             FSDirectory dir = FSDirectory.open(path);
             DirectoryReader reader = DirectoryReader.open(dir)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new BM25Similarity());

            Query query = buildQuery(queryText, analyzer);
            if (query == null) {
                return List.of();
            }

            TopDocs topDocs = searcher.search(query, topK);
            List<SearchHit> hits = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(scoreDoc.doc);
                hits.add(new SearchHit(doc.get(FIELD_ID), scoreDoc.score));
            }
            return hits;
        }
    }

    /** An index directory with actual segment files in it, not just our version marker. */
    private static boolean hasIndexFiles(Path path) {
        File[] files = path.toFile().listFiles();
        if (files == null) {
            return false;
        }
        for (File f : files) {
            if (!f.getName().equals(VERSION_FILE)) {
                return true;
            }
        }
        return false;
    }

    private Query buildQuery(String queryText, Analyzer analyzer) {
        String trimmed = queryText == null ? "" : queryText.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        QueryParser parser = new QueryParser(FIELD_CONTENT, analyzer);
        parser.setDefaultOperator(QueryParser.Operator.OR);
        try {
            return parser.parse(QueryParserBase.escape(trimmed));
        } catch (ParseException e) {
            log.debug("Falling back to a literal term query for {}", trimmed, e);
            return new TermQuery(new Term(FIELD_CONTENT, trimmed));
        }
    }
}
