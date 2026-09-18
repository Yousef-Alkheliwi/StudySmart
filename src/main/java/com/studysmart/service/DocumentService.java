package com.studysmart.service;

import com.studysmart.config.StudySmartProperties;
import com.studysmart.domain.StudyDocument;
import com.studysmart.exception.IngestionException;
import com.studysmart.exception.NotFoundException;
import com.studysmart.exception.ValidationException;
import com.studysmart.ingest.DocumentIngestionService;
import com.studysmart.repository.DocumentRepository;
import com.studysmart.search.LuceneIndexManager;
import com.studysmart.search.VectorIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".txt", ".md", ".markdown");
    private static final long MAX_SIZE_BYTES = 40L * 1024 * 1024;

    private final ProjectService projectService;
    private final DocumentRepository documentRepository;
    private final DocumentIngestionService ingestionService;
    private final LuceneIndexManager indexManager;
    private final VectorIndex vectorIndex;
    private final StudySmartProperties properties;

    public DocumentService(
            ProjectService projectService,
            DocumentRepository documentRepository,
            DocumentIngestionService ingestionService,
            LuceneIndexManager indexManager,
            VectorIndex vectorIndex,
            StudySmartProperties properties
    ) {
        this.projectService = projectService;
        this.documentRepository = documentRepository;
        this.ingestionService = ingestionService;
        this.indexManager = indexManager;
        this.vectorIndex = vectorIndex;
        this.properties = properties;
    }

    public StudyDocument upload(String projectId, MultipartFile file) {
        projectService.get(projectId);

        if (file == null || file.isEmpty()) {
            throw new ValidationException("Uploaded file is empty.");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ValidationException("File is too large (40MB max).");
        }

        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String lower = originalFilename.toLowerCase();
        if (ALLOWED_EXTENSIONS.stream().noneMatch(lower::endsWith)) {
            throw new ValidationException("Unsupported file type. Upload a PDF, .txt or .md file.");
        }

        String contentType = file.getContentType() != null ? file.getContentType() : guessContentType(lower);

        try {
            Path dir = properties.uploadsDir().resolve(projectId);
            Files.createDirectories(dir);
            Path target = dir.resolve(UUID.randomUUID() + "-" + originalFilename);
            file.transferTo(target.toFile());

            StudyDocument document = documentRepository.create(
                    projectId, originalFilename, contentType, file.getSize(), target.toString());
            ingestionService.ingestAsync(document);
            return document;
        } catch (IOException e) {
            throw new IngestionException("Failed to store uploaded file " + originalFilename, e);
        }
    }

    public List<StudyDocument> listByProject(String projectId) {
        projectService.get(projectId);
        return documentRepository.findByProject(projectId);
    }

    public StudyDocument get(String id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Document not found: " + id));
    }

    public void delete(String id) {
        StudyDocument document = get(id);
        documentRepository.deleteById(id);
        vectorIndex.invalidate(document.projectId());
        try {
            indexManager.deleteDocument(document.projectId(), id);
        } catch (IOException e) {
            log.warn("Failed to remove document {} from its search index", id, e);
        }
        try {
            Files.deleteIfExists(Path.of(document.storedPath()));
        } catch (IOException e) {
            log.warn("Failed to delete stored file for document {}", id, e);
        }
    }

    private String sanitizeFilename(String raw) {
        String name = (raw == null || raw.isBlank()) ? "upload" : raw;
        // Strip any directory components a browser or client might send.
        name = Path.of(name).getFileName().toString();
        return name.replaceAll("[^a-zA-Z0-9._\\- ]", "_");
    }

    private String guessContentType(String lowerFilename) {
        if (lowerFilename.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lowerFilename.endsWith(".md") || lowerFilename.endsWith(".markdown")) {
            return "text/markdown";
        }
        return "text/plain";
    }
}
