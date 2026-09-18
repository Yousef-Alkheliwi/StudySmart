package com.studysmart.search;

import com.studysmart.embedding.EmbeddingModel;
import com.studysmart.embedding.Vectors;
import com.studysmart.repository.ChunkEmbeddingRepository;
import com.studysmart.repository.ChunkEmbeddingRepository.StoredVector;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dense (semantic) nearest-neighbour search over a project's chunk vectors.
 *
 * <p>Vectors live in SQLite and are pulled into memory per project on first
 * use, then kept until that project's index changes. Scoring is an exact
 * brute-force cosine scan - for a course's worth of material (thousands of
 * chunks, not millions) that is a few milliseconds, which is a better
 * trade than an approximate index whose recall you then have to tune.
 */
@Component
public class VectorIndex {

    private final ChunkEmbeddingRepository repository;
    private final Map<String, List<StoredVector>> cache = new ConcurrentHashMap<>();

    public VectorIndex(ChunkEmbeddingRepository repository) {
        this.repository = repository;
    }

    public List<SearchHit> search(String projectId, EmbeddingModel model, String query, int topK) {
        List<StoredVector> vectors = cache.computeIfAbsent(
                cacheKey(projectId, model), k -> repository.findByProject(projectId, model.name()));
        if (vectors.isEmpty()) {
            return List.of();
        }

        float[] q = model.embed(query);
        PriorityQueue<SearchHit> best = new PriorityQueue<>((a, b) -> Float.compare(a.score(), b.score()));
        for (StoredVector v : vectors) {
            if (v.vector().length != q.length) {
                continue;
            }
            float score = Vectors.dot(q, v.vector());
            if (best.size() < topK) {
                best.add(new SearchHit(v.chunkId(), score));
            } else if (score > best.peek().score()) {
                best.poll();
                best.add(new SearchHit(v.chunkId(), score));
            }
        }

        List<SearchHit> hits = new ArrayList<>(best);
        hits.sort((a, b) -> Float.compare(b.score(), a.score()));
        return hits;
    }

    /** Call after any chunk vectors in the project are added or removed. */
    public void invalidate(String projectId) {
        cache.keySet().removeIf(key -> key.startsWith(projectId + "|"));
    }

    private static String cacheKey(String projectId, EmbeddingModel model) {
        return projectId + "|" + model.name();
    }
}
