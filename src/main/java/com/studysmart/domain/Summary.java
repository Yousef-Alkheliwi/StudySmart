package com.studysmart.domain;

import java.time.Instant;
import java.util.List;

public record Summary(
        String id,
        String projectId,
        String title,
        List<String> documentIds,
        String content,
        List<Citation> citations,
        Instant createdAt
) {
}
