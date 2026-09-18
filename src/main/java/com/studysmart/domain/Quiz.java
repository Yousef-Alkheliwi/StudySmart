package com.studysmart.domain;

import java.time.Instant;
import java.util.List;

public record Quiz(
        String id,
        String projectId,
        String title,
        List<String> documentIds,
        List<QuizQuestion> questions,
        Instant createdAt
) {
}
