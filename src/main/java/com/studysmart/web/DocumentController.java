package com.studysmart.web;

import com.studysmart.domain.StudyDocument;
import com.studysmart.service.DocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(value = "/api/projects/{projectId}/documents", consumes = "multipart/form-data")
    public ResponseEntity<StudyDocument> upload(@PathVariable String projectId, @RequestParam("file") MultipartFile file) {
        StudyDocument document = documentService.upload(projectId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(document);
    }

    @GetMapping("/api/projects/{projectId}/documents")
    public List<StudyDocument> listByProject(@PathVariable String projectId) {
        return documentService.listByProject(projectId);
    }

    @GetMapping("/api/documents/{id}")
    public StudyDocument get(@PathVariable String id) {
        return documentService.get(id);
    }

    @DeleteMapping("/api/documents/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
