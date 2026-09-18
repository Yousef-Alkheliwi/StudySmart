package com.studysmart.domain;

import java.time.Instant;

public record ChatSession(String id, String projectId, String title, Instant createdAt, Instant updatedAt) {
}
