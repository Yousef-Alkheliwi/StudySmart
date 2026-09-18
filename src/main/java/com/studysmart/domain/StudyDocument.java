package com.studysmart.domain;

import java.time.Instant;

/**
 * A single uploaded source file (PDF, plain text or Markdown) within a
 * project. Named {@code StudyDocument} rather than {@code Document} to avoid
 * colliding with {@code org.w3c.dom.Document} / Lucene's own {@code Document}
 * elsewhere in the codebase.
 */
public record StudyDocument(
        String id,
        String projectId,
        String filename,
        String contentType,
        long sizeBytes,
        Integer pageCount,
        String storedPath,
        DocumentStatus status,
        String errorMessage,
        Instant createdAt
) {
}
