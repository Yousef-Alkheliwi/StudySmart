package com.studysmart.service;

import com.studysmart.domain.ChatMessage;
import com.studysmart.domain.ChatSession;
import com.studysmart.exception.NotFoundException;
import com.studysmart.repository.ChatMessageRepository;
import com.studysmart.repository.ChatSessionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ChatSessionService {

    private final ProjectService projectService;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;

    public ChatSessionService(ProjectService projectService, ChatSessionRepository sessionRepository, ChatMessageRepository messageRepository) {
        this.projectService = projectService;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    public ChatSession create(String projectId, String title) {
        projectService.get(projectId);
        String effectiveTitle = (title == null || title.isBlank()) ? "New chat" : title.trim();
        return sessionRepository.create(projectId, effectiveTitle);
    }

    public List<ChatSession> listByProject(String projectId) {
        projectService.get(projectId);
        return sessionRepository.findByProject(projectId);
    }

    public ChatSession get(String id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Chat session not found: " + id));
    }

    public List<ChatMessage> messages(String sessionId) {
        get(sessionId);
        return messageRepository.findBySession(sessionId);
    }

    public void delete(String id) {
        get(id);
        sessionRepository.deleteById(id);
    }
}
