package com.studysmart.repository;

import com.studysmart.domain.ChatMessage;
import com.studysmart.domain.Citation;
import com.studysmart.domain.MessageRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class ChatMessageRepository {

    private final JdbcTemplate jdbc;

    public ChatMessageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ChatMessage save(String sessionId, MessageRole role, String content, List<Citation> citations) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbc.update(
                "INSERT INTO chat_messages (id, session_id, role, content, created_at) VALUES (?,?,?,?,?)",
                id, sessionId, role.name(), content, now.toString()
        );
        List<Citation> saved = citations == null ? List.of() : citations;
        for (Citation c : saved) {
            jdbc.update(
                    "INSERT INTO message_citations (id, message_id, citation_order, document_id, chunk_id, page, quoted_text) VALUES (?,?,?,?,?,?,?)",
                    UUID.randomUUID().toString(), id, c.order(), c.documentId(), c.chunkId(), c.page(), c.quotedText()
            );
        }
        return new ChatMessage(id, sessionId, role, content, saved, now);
    }

    public List<ChatMessage> findBySession(String sessionId) {
        Map<String, List<Citation>> citationsByMessage = new HashMap<>();
        jdbc.query(
                "SELECT c.*, d.filename FROM message_citations c "
                        + "JOIN chat_messages m ON m.id = c.message_id "
                        + "LEFT JOIN documents d ON d.id = c.document_id "
                        + "WHERE m.session_id = ? ORDER BY c.citation_order",
                rs -> {
                    Citation citation = new Citation(rs.getInt("citation_order"), rs.getString("document_id"),
                            rs.getString("filename"), rs.getString("chunk_id"), (Integer) rs.getObject("page"),
                            rs.getString("quoted_text"));
                    citationsByMessage.computeIfAbsent(rs.getString("message_id"), k -> new ArrayList<>()).add(citation);
                },
                sessionId
        );

        return jdbc.query(
                "SELECT * FROM chat_messages WHERE session_id = ? ORDER BY created_at ASC",
                (rs, n) -> new ChatMessage(
                        rs.getString("id"),
                        rs.getString("session_id"),
                        MessageRole.valueOf(rs.getString("role")),
                        rs.getString("content"),
                        citationsByMessage.getOrDefault(rs.getString("id"), List.of()),
                        Instant.parse(rs.getString("created_at"))
                ),
                sessionId
        );
    }
}
