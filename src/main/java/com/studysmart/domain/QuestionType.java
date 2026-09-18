package com.studysmart.domain;

public enum QuestionType {
    MULTIPLE_CHOICE,
    SHORT_ANSWER,
    FLASHCARD;

    /** Tolerantly parses the free-form type string a model returns ("multiple choice", "MCQ", ...). */
    public static QuestionType fromModelString(String raw) {
        if (raw == null) {
            return SHORT_ANSWER;
        }
        String normalized = raw.trim().toLowerCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "multiple_choice", "mcq", "multiplechoice" -> MULTIPLE_CHOICE;
            case "flashcard", "flash_card" -> FLASHCARD;
            default -> SHORT_ANSWER;
        };
    }
}
