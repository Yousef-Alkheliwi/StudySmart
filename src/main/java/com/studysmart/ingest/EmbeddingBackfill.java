package com.studysmart.ingest;

import com.studysmart.domain.Chunk;
import com.studysmart.embedding.EmbeddingModel;
import com.studysmart.embedding.EmbeddingModelProvider;
import com.studysmart.repository.ChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * After startup, embeds any chunk that has no vector from the active model:
 * material ingested by an older build, or while the model was still loading,
 * or after switching embedding models. Runs in the background in batches so
 * a big library doesn't delay the first request.
 */
@Component
public class EmbeddingBackfill {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingBackfill.class);
    private static final int BATCH = 64;

    private final ChunkRepository chunkRepository;
    private final EmbeddingModelProvider embeddings;
    private final DocumentIngestionService ingestion;

    public EmbeddingBackfill(ChunkRepository chunkRepository, EmbeddingModelProvider embeddings, DocumentIngestionService ingestion) {
        this.chunkRepository = chunkRepository;
        this.embeddings = embeddings;
        this.ingestion = ingestion;
    }

    @Async("ingestionExecutor")
    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        Optional<EmbeddingModel> model = embeddings.ready();
        if (model.isEmpty()) {
            return;
        }
        int total = 0;
        while (true) {
            List<Chunk> missing = chunkRepository.findWithoutEmbedding(model.get().name(), BATCH);
            if (missing.isEmpty()) {
                break;
            }
            Map<String, List<Chunk>> byProject = new LinkedHashMap<>();
            for (Chunk c : missing) {
                byProject.computeIfAbsent(c.projectId(), k -> new java.util.ArrayList<>()).add(c);
            }
            byProject.forEach(ingestion::embedChunks);
            total += missing.size();
        }
        if (total > 0) {
            log.info("Backfilled semantic vectors for {} chunks with {}", total, model.get().name());
        }
    }
}
