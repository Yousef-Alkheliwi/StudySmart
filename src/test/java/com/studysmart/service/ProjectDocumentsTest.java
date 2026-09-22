package com.studysmart.service;

import com.studysmart.domain.DocumentStatus;
import com.studysmart.domain.StudyDocument;
import com.studysmart.exception.ValidationException;
import com.studysmart.repository.DocumentRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Quizzes and summaries take document ids straight from the request. Without
 * this check, naming another project's document produced a quiz built from a
 * different course's material and filed it under this one.
 */
class ProjectDocumentsTest {

    private static StudyDocument doc(String id, String projectId, String filename) {
        return new StudyDocument(id, projectId, filename, "text/plain", 10, null, "/x",
                DocumentStatus.READY, null, Instant.EPOCH);
    }

    @Test
    void acceptsDocumentsThatBelongToTheProject() {
        DocumentRepository repository = mock(DocumentRepository.class);
        when(repository.findByIds(any())).thenReturn(List.of(doc("d1", "p1", "a.txt"), doc("d2", "p1", "b.txt")));

        assertThat(ProjectDocuments.require(repository, "p1", List.of("d1", "d2")))
                .containsOnlyKeys("d1", "d2");
    }

    @Test
    void refusesADocumentFromAnotherProject() {
        DocumentRepository repository = mock(DocumentRepository.class);
        when(repository.findByIds(any())).thenReturn(List.of(doc("d1", "p1", "mine.txt"), doc("d9", "OTHER", "theirs.txt")));

        assertThatThrownBy(() -> ProjectDocuments.require(repository, "p1", List.of("d1", "d9")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("theirs.txt")
                .hasMessageContaining("different project");
    }

    @Test
    void refusesADocumentThatDoesNotExist() {
        DocumentRepository repository = mock(DocumentRepository.class);
        when(repository.findByIds(any())).thenReturn(List.of());

        assertThatThrownBy(() -> ProjectDocuments.require(repository, "p1", List.of("ghost")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ghost");
    }
}
