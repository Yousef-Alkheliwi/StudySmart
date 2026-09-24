package com.studysmart.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Rewrites timestamps written before {@link Timestamps} fixed their width.
 *
 * <p>Rows are ordered by comparing these columns as text, so a mix of widths
 * sorts wrongly. New rows are written padded; this brings the existing ones
 * into line once, so ordering is consistent across the whole table rather
 * than only among recent rows.
 */
@Component
public class TimestampNormalizer {

    private static final Logger log = LoggerFactory.getLogger(TimestampNormalizer.class);

    /** table -> timestamp columns to normalise. */
    private static final Map<String, List<String>> COLUMNS = Map.of(
            "projects", List.of("created_at"),
            "documents", List.of("created_at"),
            "chat_sessions", List.of("created_at", "updated_at"),
            "chat_messages", List.of("created_at"),
            "quizzes", List.of("created_at"),
            "summaries", List.of("created_at"));

    private final JdbcTemplate jdbc;

    public TimestampNormalizer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void normalise() {
        int rewritten = 0;
        for (Map.Entry<String, List<String>> table : COLUMNS.entrySet()) {
            for (String column : table.getValue()) {
                rewritten += normaliseColumn(table.getKey(), column);
            }
        }
        if (rewritten > 0) {
            log.info("Normalised {} timestamps so ordering is consistent", rewritten);
        }
    }

    private int normaliseColumn(String table, String column) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, " + column + " AS value FROM " + table + " WHERE length(" + column + ") <> ?",
                Timestamps.STORED_LENGTH);
        int rewritten = 0;
        for (Map<String, Object> row : rows) {
            String value = (String) row.get("value");
            if (value == null) {
                continue;
            }
            try {
                jdbc.update("UPDATE " + table + " SET " + column + " = ? WHERE id = ?",
                        Timestamps.store(Timestamps.parse(value)), row.get("id"));
                rewritten++;
            } catch (RuntimeException e) {
                log.warn("Leaving {}.{} of row {} as it is - not a timestamp we recognise: {}",
                        table, column, row.get("id"), value);
            }
        }
        return rewritten;
    }
}
