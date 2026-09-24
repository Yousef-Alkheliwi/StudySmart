package com.studysmart.repository;

import com.studysmart.domain.ChatSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ChatSessionRepository {

    private static final RowMapper<ChatSession> MAPPER = (rs, rowNum) -> new ChatSession(
            rs.getString("id"),
            rs.getString("project_id"),
            rs.getString("title"),
            Timestamps.parse(rs.getString("created_at")),
            Timestamps.parse(rs.getString("updated_at"))
    );

    private final JdbcTemplate jdbc;

    public ChatSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ChatSession create(String projectId, String title) {
        Instant now = Instant.now();
        ChatSession session = new ChatSession(UUID.randomUUID().toString(), projectId, title, now, now);
        jdbc.update(
                "INSERT INTO chat_sessions (id, project_id, title, created_at, updated_at) VALUES (?,?,?,?,?)",
                session.id(), session.projectId(), session.title(), Timestamps.store(now), Timestamps.store(now)
        );
        return session;
    }

    public void touch(String id) {
        jdbc.update("UPDATE chat_sessions SET updated_at = ? WHERE id = ?", Timestamps.store(Instant.now()), id);
    }

    public List<ChatSession> findByProject(String projectId) {
        return jdbc.query("SELECT * FROM chat_sessions WHERE project_id = ? ORDER BY updated_at DESC", MAPPER, projectId);
    }

    public Optional<ChatSession> findById(String id) {
        return jdbc.query("SELECT * FROM chat_sessions WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public boolean deleteById(String id) {
        return jdbc.update("DELETE FROM chat_sessions WHERE id = ?", id) > 0;
    }
}
