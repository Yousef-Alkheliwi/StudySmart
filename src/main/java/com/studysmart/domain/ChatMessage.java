package com.studysmart.domain;

import java.time.Instant;
import java.util.List;

/** One turn of a conversation. */
public record ChatMessage(
        String id,
        String sessionId,
        MessageRole role,
        String content,
        List<Citation> citations,
        Instant createdAt
) {
}
