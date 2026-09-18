package com.studysmart.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysmart.domain.QuestionType;
import com.studysmart.domain.Quiz;
import com.studysmart.domain.QuizQuestion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class QuizRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public QuizRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Quiz save(String projectId, String title, List<String> documentIds, List<QuizQuestion> questions) {
        String quizId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        jdbc.update(
                "INSERT INTO quizzes (id, project_id, title, document_ids, created_at) VALUES (?,?,?,?,?)",
                quizId, projectId, title, writeJson(documentIds), now.toString()
        );

        List<QuizQuestion> saved = new ArrayList<>();
        int ordinal = 0;
        for (QuizQuestion q : questions) {
            String questionId = UUID.randomUUID().toString();
            jdbc.update(
                    "INSERT INTO quiz_questions (id, quiz_id, ordinal, type, prompt, choices, answer, explanation, source_document_id, source_filename, source_page) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    questionId, quizId, ordinal, q.type().name(), q.prompt(), writeJson(q.choices()),
                    q.answer(), q.explanation(), q.sourceDocumentId(), q.sourceFilename(), q.sourcePage()
            );
            saved.add(new QuizQuestion(questionId, quizId, ordinal, q.type(), q.prompt(), q.choices(), q.answer(),
                    q.explanation(), q.sourceDocumentId(), q.sourceFilename(), q.sourcePage()));
            ordinal++;
        }

        return new Quiz(quizId, projectId, title, documentIds, saved, now);
    }

    public Optional<Quiz> findById(String id) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM quizzes WHERE id = ?", id);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);
        List<QuizQuestion> questions = jdbc.query(
                "SELECT * FROM quiz_questions WHERE quiz_id = ? ORDER BY ordinal",
                (rs, n) -> new QuizQuestion(
                        rs.getString("id"), rs.getString("quiz_id"), rs.getInt("ordinal"),
                        QuestionType.valueOf(rs.getString("type")), rs.getString("prompt"),
                        readStringList(rs.getString("choices")), rs.getString("answer"), rs.getString("explanation"),
                        rs.getString("source_document_id"), rs.getString("source_filename"), (Integer) rs.getObject("source_page")
                ),
                id
        );
        return Optional.of(new Quiz(
                (String) row.get("id"), (String) row.get("project_id"), (String) row.get("title"),
                readStringList((String) row.get("document_ids")), questions,
                Instant.parse((String) row.get("created_at"))
        ));
    }

    public List<Quiz> findByProject(String projectId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id FROM quizzes WHERE project_id = ? ORDER BY created_at DESC", projectId);
        List<Quiz> quizzes = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            findById((String) row.get("id")).ifPresent(quizzes::add);
        }
        return quizzes;
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
