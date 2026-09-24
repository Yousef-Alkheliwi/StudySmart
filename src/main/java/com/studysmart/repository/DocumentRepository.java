package com.studysmart.repository;

import com.studysmart.domain.DocumentStatus;
import com.studysmart.domain.StudyDocument;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DocumentRepository {

    private static final RowMapper<StudyDocument> MAPPER = (rs, rowNum) -> new StudyDocument(
            rs.getString("id"),
            rs.getString("project_id"),
            rs.getString("filename"),
            rs.getString("content_type"),
            rs.getLong("size_bytes"),
            (Integer) rs.getObject("page_count"),
            rs.getString("stored_path"),
            DocumentStatus.valueOf(rs.getString("status")),
            rs.getString("error_message"),
            Timestamps.parse(rs.getString("created_at"))
    );

    private final JdbcTemplate jdbc;

    public DocumentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public StudyDocument create(String projectId, String filename, String contentType, long sizeBytes, String storedPath) {
        StudyDocument doc = new StudyDocument(
                UUID.randomUUID().toString(), projectId, filename, contentType, sizeBytes,
                null, storedPath, DocumentStatus.PENDING, null, Instant.now()
        );
        jdbc.update(
                "INSERT INTO documents (id, project_id, filename, content_type, size_bytes, page_count, stored_path, status, error_message, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                doc.id(), doc.projectId(), doc.filename(), doc.contentType(), doc.sizeBytes(),
                doc.pageCount(), doc.storedPath(), doc.status().name(), doc.errorMessage(), Timestamps.store(doc.createdAt())
        );
        return doc;
    }

    public void updateStatus(String id, DocumentStatus status, String errorMessage) {
        jdbc.update("UPDATE documents SET status = ?, error_message = ? WHERE id = ?", status.name(), errorMessage, id);
    }

    public void updatePageCount(String id, int pageCount) {
        jdbc.update("UPDATE documents SET page_count = ? WHERE id = ?", pageCount, id);
    }

    public List<StudyDocument> findByProject(String projectId) {
        return jdbc.query("SELECT * FROM documents WHERE project_id = ? ORDER BY created_at DESC", MAPPER, projectId);
    }

    public List<StudyDocument> findByIds(List<String> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", ids.stream().map(i -> "?").toList());
        return jdbc.query("SELECT * FROM documents WHERE id IN (" + placeholders + ")", MAPPER, ids.toArray());
    }

    public Optional<StudyDocument> findById(String id) {
        return jdbc.query("SELECT * FROM documents WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public boolean deleteById(String id) {
        return jdbc.update("DELETE FROM documents WHERE id = ?", id) > 0;
    }
}
