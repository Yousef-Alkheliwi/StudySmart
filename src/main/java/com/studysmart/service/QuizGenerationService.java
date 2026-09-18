package com.studysmart.service;

import com.studysmart.domain.Chunk;
import com.studysmart.domain.QuestionType;
import com.studysmart.domain.Quiz;
import com.studysmart.domain.QuizQuestion;
import com.studysmart.domain.StudyDocument;
import com.studysmart.exception.ValidationException;
import com.studysmart.local.ChunkSampler;
import com.studysmart.local.ClozeQuizGenerator;
import com.studysmart.local.GroundedSource;
import com.studysmart.repository.ChunkRepository;
import com.studysmart.repository.DocumentRepository;
import com.studysmart.repository.QuizRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds a quiz strictly from a project's own material; every question is traced back to its source excerpt. */
@Service
public class QuizGenerationService {

    private static final int MAX_SOURCE_CHUNKS = 40;

    private final ChunkRepository chunkRepository;
    private final DocumentRepository documentRepository;
    private final QuizRepository quizRepository;

    public QuizGenerationService(ChunkRepository chunkRepository, DocumentRepository documentRepository, QuizRepository quizRepository) {
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.quizRepository = quizRepository;
    }

    public Quiz generate(String projectId, List<String> documentIds, int questionCount, List<QuestionType> types) {
        List<Chunk> chunks = documentIds.isEmpty()
                ? chunkRepository.findByProject(projectId)
                : chunkRepository.findByDocuments(documentIds);
        if (chunks.isEmpty()) {
            throw new ValidationException("No processed material is available yet to build a quiz from.");
        }

        List<Chunk> sample = ChunkSampler.sample(chunks, MAX_SOURCE_CHUNKS);
        List<String> effectiveDocumentIds = documentIds.isEmpty()
                ? sample.stream().map(Chunk::documentId).distinct().toList()
                : documentIds;
        Map<String, StudyDocument> documentsById = documentRepository.findByIds(effectiveDocumentIds).stream()
                .collect(Collectors.toMap(StudyDocument::id, Function.identity()));
        List<GroundedSource> sources = sample.stream()
                .filter(chunk -> documentsById.containsKey(chunk.documentId()))
                .map(chunk -> new GroundedSource(chunk, documentsById.get(chunk.documentId())))
                .toList();

        List<QuizQuestion> questions = ClozeQuizGenerator.generate(sources, questionCount, types);
        if (questions.isEmpty()) {
            throw new ValidationException("No quiz questions could be generated from this material - it may be too short.");
        }
        String names = sources.stream().map(s -> s.document().filename()).distinct().collect(Collectors.joining(", "));
        return quizRepository.save(projectId, "Key terms: " + names, effectiveDocumentIds, questions);
    }
}
