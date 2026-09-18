package com.studysmart.web;

import com.studysmart.domain.ChatMessage;
import com.studysmart.domain.ChatSession;
import com.studysmart.service.ChatService;
import com.studysmart.service.ChatSessionService;
import com.studysmart.web.dto.AskRequest;
import com.studysmart.web.dto.CreateSessionRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ChatController {

    private final ChatSessionService sessionService;
    private final ChatService chatService;

    public ChatController(ChatSessionService sessionService, ChatService chatService) {
        this.sessionService = sessionService;
        this.chatService = chatService;
    }

    @PostMapping("/api/projects/{projectId}/sessions")
    public ResponseEntity<ChatSession> create(@PathVariable String projectId, @RequestBody(required = false) CreateSessionRequest request) {
        String title = request == null ? null : request.title();
        ChatSession session = sessionService.create(projectId, title);
        return ResponseEntity.status(HttpStatus.CREATED).body(session);
    }

    @GetMapping("/api/projects/{projectId}/sessions")
    public List<ChatSession> listByProject(@PathVariable String projectId) {
        return sessionService.listByProject(projectId);
    }

    @GetMapping("/api/sessions/{id}")
    public ChatSession get(@PathVariable String id) {
        return sessionService.get(id);
    }

    @GetMapping("/api/sessions/{id}/messages")
    public List<ChatMessage> messages(@PathVariable String id) {
        return sessionService.messages(id);
    }

    @PostMapping("/api/sessions/{id}/ask")
    public ChatMessage ask(@PathVariable String id, @Valid @RequestBody AskRequest request) {
        return chatService.ask(id, request.question());
    }

    @DeleteMapping("/api/sessions/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        sessionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
