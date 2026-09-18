package com.studysmart.domain;

import java.util.List;

public record QuizQuestion(
        String id,
        String quizId,
        int ordinal,
        QuestionType type,
        String prompt,
        List<String> choices,
        String answer,
        String explanation,
        String sourceDocumentId,
        String sourceFilename,
        Integer sourcePage
) {
}
