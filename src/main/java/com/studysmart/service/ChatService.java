package com.studysmart.service;

import com.studysmart.config.StudySmartProperties;
import com.studysmart.domain.ChatMessage;
import com.studysmart.domain.ChatSession;
import com.studysmart.domain.Chunk;
import com.studysmart.domain.MessageRole;
import com.studysmart.domain.StudyDocument;
import com.studysmart.embedding.EmbeddingModelProvider;
import com.studysmart.exception.NotFoundException;
import com.studysmart.local.AnswerResult;
import com.studysmart.local.ExtractiveAnswerEngine;
import com.studysmart.local.GroundedSource;
import com.studysmart.repository.ChatMessageRepository;
import com.studysmart.repository.ChatSessionRepository;
import com.studysmart.repository.DocumentRepository;
import com.studysmart.search.SearchService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The question-answering flow: hybrid-search the project for relevant
 * chunks, have the on-device extractive engine answer from them, and
 * persist both turns with their citations.
 */
@Service
public class ChatService {

    private final SearchService searchService;
    private final EmbeddingModelProvider embeddings;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final DocumentRepository documentRepository;
    private final StudySmartProperties properties;

    public ChatService(SearchService searchService, EmbeddingModelProvider embeddings,
                       ChatSessionRepository sessionRepository, ChatMessageRepository messageRepository,
                       DocumentRepository documentRepository, StudySmartProperties properties) {
        this.searchService = searchService;
        this.embeddings = embeddings;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.documentRepository = documentRepository;
        this.properties = properties;
    }

    public ChatMessage ask(String sessionId, String question) {
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Chat session not found: " + sessionId));

        messageRepository.save(sessionId, MessageRole.USER, question, List.of());

        List<Chunk> hits = searchService.search(session.projectId(), question, properties.getRetrieval().getTopK());
        if (hits.isEmpty()) {
            ChatMessage reply = messageRepository.save(sessionId, MessageRole.ASSISTANT,
                    "I couldn't find anything in this project's documents that relates to that question. "
                            + "Try uploading the relevant material, or rephrasing the question.", List.of());
            sessionRepository.touch(sessionId);
            return reply;
        }

        AnswerResult result = new ExtractiveAnswerEngine(embeddings.currentOrHashing()).answer(question, toSources(hits));
        ChatMessage reply = messageRepository.save(sessionId, MessageRole.ASSISTANT, result.text(), result.citations());
        sessionRepository.touch(sessionId);
        return reply;
    }

    private List<GroundedSource> toSources(List<Chunk> hits) {
        List<String> documentIds = hits.stream().map(Chunk::documentId).distinct().toList();
        Map<String, StudyDocument> documentsById = documentRepository.findByIds(documentIds).stream()
                .collect(Collectors.toMap(StudyDocument::id, Function.identity()));
        return hits.stream()
                .filter(chunk -> documentsById.containsKey(chunk.documentId()))
                .map(chunk -> new GroundedSource(chunk, documentsById.get(chunk.documentId())))
                .toList();
    }
}
