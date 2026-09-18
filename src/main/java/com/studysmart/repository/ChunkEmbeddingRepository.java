package com.studysmart.repository;

import com.studysmart.embedding.Vectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class ChunkEmbeddingRepository {

    /** A chunk id paired with its (normalized) vector. */
    public record StoredVector(String chunkId, float[] vector) {
    }

    private final JdbcTemplate jdbc;

    public ChunkEmbeddingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveAll(String projectId, String modelName, List<String> chunkIds, List<float[]> vectors) {
        if (chunkIds.size() != vectors.size()) {
            throw new IllegalArgumentException("chunkIds and vectors differ in length");
        }
        List<Object[]> rows = new ArrayList<>(chunkIds.size());
        for (int i = 0; i < chunkIds.size(); i++) {
            float[] v = vectors.get(i);
            rows.add(new Object[]{chunkIds.get(i), projectId, modelName, v.length, Vectors.toBytes(v)});
        }
        jdbc.batchUpdate(
                "INSERT OR REPLACE INTO chunk_embeddings (chunk_id, project_id, model, dims, vector) VALUES (?,?,?,?,?)",
                rows);
    }

    /** Every vector in a project that was produced by {@code modelName} - vectors from a different model are ignored, never compared. */
    public List<StoredVector> findByProject(String projectId, String modelName) {
        return jdbc.query(
                "SELECT chunk_id, vector FROM chunk_embeddings WHERE project_id = ? AND model = ?",
                (rs, n) -> new StoredVector(rs.getString("chunk_id"), Vectors.fromBytes(rs.getBytes("vector"))),
                projectId, modelName);
    }

    public int countByProject(String projectId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM chunk_embeddings WHERE project_id = ?", Integer.class, projectId);
        return count == null ? 0 : count;
    }
}
