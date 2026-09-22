package com.studysmart.repository;

import com.studysmart.domain.Chunk;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ChunkRepository {

    private static final RowMapper<Chunk> MAPPER = (rs, rowNum) -> new Chunk(
            rs.getString("id"),
            rs.getString("document_id"),
            rs.getString("project_id"),
            rs.getInt("ordinal"),
            (Integer) rs.getObject("page"),
            rs.getString("content"),
            rs.getInt("char_start"),
            rs.getInt("char_end")
    );

    private final JdbcTemplate jdbc;

    public ChunkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveAll(List<Chunk> chunks) {
        jdbc.batchUpdate(
                "INSERT INTO chunks (id, document_id, project_id, ordinal, page, content, char_start, char_end) VALUES (?,?,?,?,?,?,?,?)",
                chunks,
                chunks.size(),
                (ps, chunk) -> {
                    ps.setString(1, chunk.id());
                    ps.setString(2, chunk.documentId());
                    ps.setString(3, chunk.projectId());
                    ps.setInt(4, chunk.ordinal());
                    if (chunk.page() != null) {
                        ps.setInt(5, chunk.page());
                    } else {
                        ps.setNull(5, java.sql.Types.INTEGER);
                    }
                    ps.setString(6, chunk.content());
                    ps.setInt(7, chunk.charStart());
                    ps.setInt(8, chunk.charEnd());
                }
        );
    }

    public List<Chunk> findByIds(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
        return jdbc.query("SELECT * FROM chunks WHERE id IN (" + placeholders + ")", MAPPER, ids.toArray());
    }

    public Optional<Chunk> findById(String id) {
        return jdbc.query("SELECT * FROM chunks WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<Chunk> findByDocument(String documentId) {
        return jdbc.query("SELECT * FROM chunks WHERE document_id = ? ORDER BY ordinal", MAPPER, documentId);
    }

    /** Only chunks of documents that finished ingesting: a failed or half-processed file must not feed quizzes or summaries. */
    public List<Chunk> findByProject(String projectId) {
        return jdbc.query("SELECT c.* FROM chunks c JOIN documents d ON d.id = c.document_id "
                + "WHERE c.project_id = ? AND d.status = 'READY' ORDER BY c.document_id, c.ordinal", MAPPER, projectId);
    }

    public int deleteByDocument(String documentId) {
        return jdbc.update("DELETE FROM chunks WHERE document_id = ?", documentId);
    }

    /** Scoped to the project on purpose: a document id from another project must never return rows here. */
    public List<Chunk> findByDocuments(String projectId, List<String> documentIds) {
        if (documentIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", documentIds.stream().map(i -> "?").toList());
        List<Object> args = new java.util.ArrayList<>();
        args.add(projectId);
        args.addAll(documentIds);
        return jdbc.query("SELECT * FROM chunks WHERE project_id = ? AND document_id IN (" + placeholders + ") "
                + "ORDER BY document_id, ordinal", MAPPER, args.toArray());
    }

    /** Chunks of READY documents that have no vector from {@code modelName} yet - what a startup backfill needs to embed. */
    public List<Chunk> findWithoutEmbedding(String modelName, int limit) {
        return jdbc.query(
                "SELECT c.* FROM chunks c "
                        + "JOIN documents d ON d.id = c.document_id AND d.status = 'READY' "
                        + "LEFT JOIN chunk_embeddings e ON e.chunk_id = c.id AND e.model = ? "
                        + "WHERE e.chunk_id IS NULL ORDER BY c.project_id, c.document_id, c.ordinal LIMIT ?",
                MAPPER, modelName, limit);
    }

    public int countByDocument(String documentId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM chunks WHERE document_id = ?", Integer.class, documentId);
        return count == null ? 0 : count;
    }
}
