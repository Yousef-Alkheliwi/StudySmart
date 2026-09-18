package com.studysmart.service;

import com.studysmart.domain.Chunk;
import com.studysmart.domain.StudyDocument;
import com.studysmart.domain.Summary;
import com.studysmart.embedding.EmbeddingModelProvider;
import com.studysmart.exception.ValidationException;
import com.studysmart.local.AnswerResult;
import com.studysmart.local.ChunkSampler;
import com.studysmart.local.ExtractiveSummarizer;
import com.studysmart.local.GroundedSource;
import com.studysmart.repository.ChunkRepository;
import com.studysmart.repository.DocumentRepository;
import com.studysmart.repository.SummaryRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Summarizes a chosen set of documents with the on-device extractive summarizer. */
@Service
public class SummaryService {

    private static final int MAX_SUMMARY_CHUNKS = 60;

    private final ChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;
    private final EmbeddingModelProvider embeddings;
    private final SummaryRepository summaryRepository;

    public SummaryService(ChunkRepository chunkRepository, DocumentRepository documentRepository,
                          EmbeddingModelProvider embeddings, SummaryRepository summaryRepository) {
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.embeddings = embeddings;
        this.summaryRepository = summaryRepository;
    }

    public Summary summarize(String projectId, List<String> documentIds) {
        if (documentIds.isEmpty()) {
            throw new ValidationException("Select at least one document to summarize.");
        }
        List<Chunk> chunks = chunkRepository.findByDocuments(documentIds);
        if (chunks.isEmpty()) {
            throw new ValidationException("The selected documents have no processed material yet - wait for ingestion to finish.");
        }

        Map<String, StudyDocument> documentsById = documentRepository.findByIds(documentIds).stream()
                .collect(Collectors.toMap(StudyDocument::id, Function.identity()));
        List<GroundedSource> sources = ChunkSampler.sample(chunks, MAX_SUMMARY_CHUNKS).stream()
                .filter(chunk -> documentsById.containsKey(chunk.documentId()))
                .map(chunk -> new GroundedSource(chunk, documentsById.get(chunk.documentId())))
                .toList();

        String documentNames = sources.stream().map(s -> s.document().filename()).distinct()
                .collect(Collectors.joining(", "));

        AnswerResult result = new ExtractiveSummarizer(embeddings.awaitReady(Duration.ofSeconds(8))).summarize(sources);
        return summaryRepository.save(projectId, "Summary of " + documentNames, documentIds, result.text(), result.citations());
    }
}
