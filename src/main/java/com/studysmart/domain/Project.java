package com.studysmart.domain;

import java.time.Instant;

/**
 * A course or research project. Every document, chat session, quiz and
 * summary belongs to exactly one project, which is how StudySmart keeps
 * material from different courses from bleeding into each other's search
 * results and answers.
 */
public record Project(String id, String name, String description, Instant createdAt) {
}
