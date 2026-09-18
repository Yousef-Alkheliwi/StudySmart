package com.studysmart.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysmart.domain.Citation;
import com.studysmart.domain.Summary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SummaryRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public SummaryRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Summary save(String projectId, String title, List<String> documentIds, String content, List<Citation> citations) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO summaries (id, project_id, title, document_ids, content, created_at) VALUES (?,?,?,?,?,?)",
                id, projectId, title, writeJson(documentIds), content, now.toString()
        );
        for (Citation c : citations) {
            jdbc.update(
                    "INSERT INTO summary_citations (id, summary_id, document_id, page, quoted_text) VALUES (?,?,?,?,?)",
                    UUID.randomUUID().toString(), id, c.documentId(), c.page(), c.quotedText()
            );
        }
        return new Summary(id, projectId, title, documentIds, content, citations, now);
    }

    public Optional<Summary> findById(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM summaries WHERE id = ?", id);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);
        List<Citation> citations = jdbc.query(
                "SELECT c.*, d.filename FROM summary_citations c LEFT JOIN documents d ON d.id = c.document_id "
                        + "WHERE c.summary_id = ?",
                (rs, n) -> new Citation(n, rs.getString("document_id"), rs.getString("filename"), null,
                        (Integer) rs.getObject("page"), rs.getString("quoted_text")),
                id
        );
        return Optional.of(new Summary(
                (String) row.get("id"), (String) row.get("project_id"), (String) row.get("title"),
                readStringList((String) row.get("document_ids")), (String) row.get("content"), citations,
                Instant.parse((String) row.get("created_at"))
        ));
    }

    public List<Summary> findByProject(String projectId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id FROM summaries WHERE project_id = ? ORDER BY created_at DESC", projectId);
        List<Summary> summaries = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            findById((String) row.get("id")).ifPresent(summaries::add);
        }
        return summaries;
    }

    private String writeJson(List<String> values) {
        try {
            return json.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize list to JSON", e);
        }
    }

    private List<String> readStringList(String jsonText) {
        if (jsonText == null || jsonText.isBlank()) {
            return List.of();
        }
        try {
            return json.readValue(jsonText, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse JSON list", e);
        }
    }
}
