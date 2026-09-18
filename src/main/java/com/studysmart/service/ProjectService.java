package com.studysmart.service;

import com.studysmart.config.StudySmartProperties;
import com.studysmart.domain.Project;
import com.studysmart.exception.NotFoundException;
import com.studysmart.exception.ValidationException;
import com.studysmart.repository.ProjectRepository;
import com.studysmart.search.LuceneIndexManager;
import com.studysmart.search.VectorIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final LuceneIndexManager indexManager;
    private final VectorIndex vectorIndex;
    private final StudySmartProperties properties;

    public ProjectService(ProjectRepository projectRepository, LuceneIndexManager indexManager,
                          VectorIndex vectorIndex, StudySmartProperties properties) {
        this.projectRepository = projectRepository;
        this.indexManager = indexManager;
        this.vectorIndex = vectorIndex;
        this.properties = properties;
    }

    public Project create(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("Project name is required.");
        }
        return projectRepository.create(name.trim(), description == null ? null : description.trim());
    }

    public List<Project> list() {
        return projectRepository.findAll();
    }

    public Project get(String id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Project not found: " + id));
    }

    /** Deletes the project row (cascading to its documents, chunks, vectors, sessions, quizzes, summaries), its search indexes and uploaded files. */
    public void delete(String id) {
        get(id);
        projectRepository.deleteById(id);
        indexManager.deleteProject(id);
        vectorIndex.invalidate(id);
        deleteUploadsDir(id);
    }

    private void deleteUploadsDir(String projectId) {
        Path dir = properties.uploadsDir().resolve(projectId);
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    log.warn("Failed to delete {}", p, e);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to walk uploads directory for project {}", projectId, e);
        }
    }
}
