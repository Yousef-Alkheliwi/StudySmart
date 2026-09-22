package com.studysmart.ingest;

import com.studysmart.config.StudySmartProperties;
import com.studysmart.domain.Chunk;
import com.studysmart.domain.DocumentStatus;
import com.studysmart.domain.StudyDocument;
import com.studysmart.embedding.EmbeddingModelProvider;
import com.studysmart.repository.ChunkEmbeddingRepository;
import com.studysmart.repository.ChunkRepository;
import com.studysmart.repository.DocumentRepository;
import com.studysmart.search.LuceneIndexManager;
import com.studysmart.search.VectorIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ingestion persists chunks before it indexes and embeds them. When a later
 * step fails, the chunks already written must not survive - otherwise a
 * document marked FAILED still feeds search, quizzes and summaries.
 */
class IngestionFailureTest {

    @TempDir
    Path tempDir;

    @Test
    void rollsBackChunksWhenIndexingFails() throws Exception {
        Path file = tempDir.resolve("notes.txt");
        Files.writeString(file, "Cellular respiration releases energy from glucose. "
                + "Glycolysis happens in the cytoplasm of the cell.");

        ChunkRepository chunks = mock(ChunkRepository.class);
        DocumentRepository documents = mock(DocumentRepository.class);
        LuceneIndexManager index = mock(LuceneIndexManager.class);
        VectorIndex vectors = mock(VectorIndex.class);
        EmbeddingModelProvider embeddings = mock(EmbeddingModelProvider.class);
        ChunkEmbeddingRepository embeddingRepository = mock(ChunkEmbeddingRepository.class);

        // The keyword index blows up after the chunks have already been saved.
        doThrow(new IOException("disk full")).when(index).indexChunks(anyString(), any());
        when(chunks.deleteByDocument(anyString())).thenReturn(2);

        DocumentIngestionService service = new DocumentIngestionService(
                List.of(new PlainTextExtractor()), chunks, embeddingRepository, documents, index, vectors,
                embeddings, new StudySmartProperties());

        StudyDocument document = new StudyDocument("d1", "p1", "notes.txt", "text/plain",
                Files.size(file), null, file.toString(), DocumentStatus.PENDING, null, Instant.now());

        service.ingestAsync(document);

        verify(chunks).deleteByDocument("d1");
        verify(index).deleteDocument("p1", "d1");
        verify(vectors).invalidate("p1");
        verify(documents).updateStatus(eqId(), eqFailed(), anyString());
    }

    private static String eqId() {
        return org.mockito.ArgumentMatchers.eq("d1");
    }

    private static DocumentStatus eqFailed() {
        return org.mockito.ArgumentMatchers.eq(DocumentStatus.FAILED);
    }

    @Test
    void keepsChunksWhenEverythingSucceeds() throws Exception {
        Path file = tempDir.resolve("ok.txt");
        Files.writeString(file, "Mitochondria generate most of the cell's ATP for the body. "
                + "Enzymes lower the activation energy that a reaction needs.");

        ChunkRepository chunks = mock(ChunkRepository.class);
        DocumentRepository documents = mock(DocumentRepository.class);
        LuceneIndexManager index = mock(LuceneIndexManager.class);
        VectorIndex vectors = mock(VectorIndex.class);
        EmbeddingModelProvider embeddings = mock(EmbeddingModelProvider.class);
        when(embeddings.ready()).thenReturn(java.util.Optional.empty());

        DocumentIngestionService service = new DocumentIngestionService(
                List.of(new PlainTextExtractor()), chunks, mock(ChunkEmbeddingRepository.class), documents,
                index, vectors, embeddings, new StudySmartProperties());

        StudyDocument document = new StudyDocument("d2", "p1", "ok.txt", "text/plain",
                Files.size(file), null, file.toString(), DocumentStatus.PENDING, null, Instant.now());

        service.ingestAsync(document);

        List<Chunk> saved = new ArrayList<>();
        verify(chunks).saveAll(org.mockito.ArgumentMatchers.argThat(list -> saved.addAll(list)));
        assertThat(saved).isNotEmpty();
        verify(chunks, org.mockito.Mockito.never()).deleteByDocument(anyString());
        verify(documents).updateStatus("d2", DocumentStatus.READY, null);
    }
}
