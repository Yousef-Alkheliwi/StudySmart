package com.studysmart.service;

import com.studysmart.domain.StudyDocument;
import com.studysmart.exception.ValidationException;
import com.studysmart.repository.DocumentRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves the documents a request names, refusing any that belong to a
 * different project.
 *
 * <p>Keeping one course's material out of another course's quizzes and
 * summaries is the whole point of projects. The web UI only ever offers a
 * project's own documents, but the API took the ids on trust, so a request
 * naming another project's document produced - and stored - a summary of
 * material from a course it had nothing to do with.
 */
final class ProjectDocuments {

    private ProjectDocuments() {
    }

    /** The named documents, keyed by id, or a validation failure if any of them is missing or belongs elsewhere. */
    static Map<String, StudyDocument> require(DocumentRepository documents, String projectId, List<String> documentIds) {
        Map<String, StudyDocument> found = new LinkedHashMap<>();
        for (StudyDocument document : documents.findByIds(documentIds)) {
            found.put(document.id(), document);
        }
        for (String id : documentIds) {
            StudyDocument document = found.get(id);
            if (document == null) {
                throw new ValidationException("Document not found: " + id);
            }
            if (!document.projectId().equals(projectId)) {
                throw new ValidationException("Document " + document.filename() + " belongs to a different project.");
            }
        }
        return found;
    }
}
