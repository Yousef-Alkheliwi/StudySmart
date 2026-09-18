package com.studysmart.domain;

/** Result of grading a student's free-text answer against a quiz question's reference answer. */
public record GradedAnswer(boolean correct, String feedback, String referenceAnswer) {
}
