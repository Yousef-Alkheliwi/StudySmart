package com.studysmart.repository;

import com.studysmart.domain.Project;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ProjectRepository {

    private static final RowMapper<Project> MAPPER = (rs, rowNum) -> new Project(
            rs.getString("id"),
            rs.getString("name"),
            rs.getString("description"),
            Instant.parse(rs.getString("created_at"))
    );

    private final JdbcTemplate jdbc;

    public ProjectRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Project create(String name, String description) {
        Project project = new Project(UUID.randomUUID().toString(), name, description, Instant.now());
        jdbc.update(
                "INSERT INTO projects (id, name, description, created_at) VALUES (?, ?, ?, ?)",
                project.id(), project.name(), project.description(), project.createdAt().toString()
        );
        return project;
    }

    public List<Project> findAll() {
        return jdbc.query("SELECT * FROM projects ORDER BY created_at DESC", MAPPER);
    }

    public Optional<Project> findById(String id) {
        return jdbc.query("SELECT * FROM projects WHERE id = ?", MAPPER, id)
                .stream().findFirst();
    }

    public boolean deleteById(String id) {
        return jdbc.update("DELETE FROM projects WHERE id = ?", id) > 0;
    }
}
