package com.studysmart.ingest;

import com.studysmart.config.StudySmartProperties;
import com.studysmart.domain.Chunk;
import com.studysmart.domain.DocumentStatus;
import com.studysmart.domain.StudyDocument;
import com.studysmart.embedding.EmbeddingModel;
import com.studysmart.embedding.EmbeddingModelProvider;
import com.studysmart.exception.IngestionException;
import com.studysmart.repository.ChunkEmbeddingRepository;
import com.studysmart.repository.ChunkRepository;
import com.studysmart.repository.DocumentRepository;
import com.studysmart.search.LuceneIndexManager;
import com.studysmart.search.VectorIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The pipeline every uploaded file goes through: extract text, split it
 * into retrieval-sized chunks, persist them, add them to the project's
 * keyword index, and embed them for semantic search. Runs off the upload
 * request thread so a large PDF doesn't block the HTTP response.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final List<TextExtractor> extractors;
    private final ChunkRepository chunkRepository;
    private final ChunkEmbeddingRepository embeddingRepository;
    private final DocumentRepository documentRepository;
    private final LuceneIndexManager indexManager;
    private final VectorIndex vectorIndex;
    private final EmbeddingModelProvider embeddings;
    private final StudySmartProperties properties;

    public DocumentIngestionService(
            List<TextExtractor> extractors,
            ChunkRepository chunkRepository,
            ChunkEmbeddingRepository embeddingRepository,
            DocumentRepository documentRepository,
            LuceneIndexManager indexManager,
            VectorIndex vectorIndex,
            EmbeddingModelProvider embeddings,
            StudySmartProperties properties
    ) {
        this.extractors = extractors;
        this.chunkRepository = chunkRepository;
        this.embeddingRepository = embeddingRepository;
        this.documentRepository = documentRepository;
        this.indexManager = indexManager;
        this.vectorIndex = vectorIndex;
        this.embeddings = embeddings;
        this.properties = properties;
    }

    @Async("ingestionExecutor")
    public void ingestAsync(StudyDocument document) {
        try {
            documentRepository.updateStatus(document.id(), DocumentStatus.PROCESSING, null);

            TextExtractor extractor = extractors.stream()
                    .filter(e -> e.supports(document.contentType(), document.filename()))
                    .findFirst()
                    .orElseThrow(() -> new IngestionException(
                            "No text extractor available for " + document.filename(), null));

            List<ExtractedPage> pages = extractor.extract(Path.of(document.storedPath()));

            long pageCount = pages.stream().map(ExtractedPage::pageNumber).filter(Objects::nonNull).distinct().count();
            if (pageCount > 0) {
                documentRepository.updatePageCount(document.id(), (int) pageCount);
            }

            Chunker chunker = new Chunker(
                    properties.getChunking().getTargetWords(),
                    properties.getChunking().getOverlapWords());
            List<ChunkCandidate> candidates = chunker.chunk(pages);

            if (candidates.isEmpty()) {
                throw new IngestionException(
                        "No extractable text was found in " + document.filename()
                                + " (empty file, or a scanned/image-only PDF with no text layer)", null);
            }

            List<Chunk> chunks = new ArrayList<>(candidates.size());
            int ordinal = 0;
            for (ChunkCandidate candidate : candidates) {
                chunks.add(new Chunk(
                        UUID.randomUUID().toString(),
                        document.id(),
                        document.projectId(),
                        ordinal++,
                        candidate.page(),
                        candidate.content(),
                        candidate.charStart(),
                        candidate.charEnd()
                ));
            }

            chunkRepository.saveAll(chunks);
            indexManager.indexChunks(document.projectId(), chunks);
            embedChunks(document.projectId(), chunks);

            documentRepository.updateStatus(document.id(), DocumentStatus.READY, null);
            log.info("Ingested {} ({} chunks)", document.filename(), chunks.size());
        } catch (Exception e) {
            log.error("Ingestion failed for document {} ({})", document.id(), document.filename(), e);
            documentRepository.updateStatus(document.id(), DocumentStatus.FAILED, safeMessage(e));
        }
    }

    /** Embeds and stores vectors for the given chunks with whichever model is ready; invalidates the project's vector cache. */
    public void embedChunks(String projectId, List<Chunk> chunks) {
        Optional<EmbeddingModel> model = embeddings.ready();
        if (model.isEmpty() || chunks.isEmpty()) {
            return;
        }
        List<float[]> vectors = model.get().embedAll(chunks.stream().map(Chunk::content).toList());
        embeddingRepository.saveAll(projectId, model.get().name(), chunks.stream().map(Chunk::id).toList(), vectors);
        vectorIndex.invalidate(projectId);
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            message = e.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
