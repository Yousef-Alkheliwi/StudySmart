package com.studysmart.search;

import com.studysmart.domain.Chunk;
import com.studysmart.embedding.EmbeddingModel;
import com.studysmart.embedding.EmbeddingModelProvider;
import com.studysmart.repository.ChunkRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Hybrid retrieval: BM25 keyword search and dense semantic search run side
 * by side over the same project, and their rankings are fused. Keyword
 * search nails exact terms ("Krebs cycle"); the semantic side catches
 * paraphrase ("how do cells make energy"). Either one alone misses things
 * the other finds.
 */
@Service
public class SearchService {

    /** Matches the answering path: a search issued while the model loads waits briefly rather than going keyword-only. */
    private static final Duration MODEL_WAIT = Duration.ofSeconds(8);

    private final LuceneIndexManager indexManager;
    private final VectorIndex vectorIndex;
    private final EmbeddingModelProvider embeddings;
    private final ChunkRepository chunkRepository;

    public SearchService(LuceneIndexManager indexManager, VectorIndex vectorIndex,
                         EmbeddingModelProvider embeddings, ChunkRepository chunkRepository) {
        this.indexManager = indexManager;
        this.vectorIndex = vectorIndex;
        this.embeddings = embeddings;
        this.chunkRepository = chunkRepository;
    }

    public List<Chunk> search(String projectId, String query, int topK) {
        // Each retriever over-fetches so the fusion has something to agree on.
        int candidates = Math.max(topK * 3, 10);

        List<SearchHit> keyword;
        try {
            keyword = indexManager.search(projectId, query, candidates);
        } catch (IOException e) {
            throw new UncheckedIOException("Keyword search failed for project " + projectId, e);
        }

        // Wait briefly at startup: searching with the stopgap model would find
        // none of the vectors the real model wrote, silently leaving only BM25.
        List<SearchHit> semantic = embeddings.current().isEmpty()
                ? List.of()
                : vectorIndex.search(projectId, embeddings.awaitReady(MODEL_WAIT), query, candidates);

        List<SearchHit> fused = RankFusion.fuse(topK, keyword, semantic);
        if (fused.isEmpty()) {
            return List.of();
        }

        List<String> orderedIds = fused.stream().map(SearchHit::chunkId).toList();
        Map<String, Chunk> byId = new LinkedHashMap<>();
        for (Chunk chunk : chunkRepository.findByIds(orderedIds)) {
            byId.put(chunk.id(), chunk);
        }
        return orderedIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }
}
