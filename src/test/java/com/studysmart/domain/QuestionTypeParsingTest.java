package com.studysmart.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QuestionTypeParsingTest {

    @Test
    void parsesKnownVariants() {
        assertThat(QuestionType.fromModelString("multiple_choice")).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(QuestionType.fromModelString("Multiple Choice")).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(QuestionType.fromModelString("MCQ")).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(QuestionType.fromModelString("flashcard")).isEqualTo(QuestionType.FLASHCARD);
        assertThat(QuestionType.fromModelString("flash-card")).isEqualTo(QuestionType.FLASHCARD);
        assertThat(QuestionType.fromModelString("short_answer")).isEqualTo(QuestionType.SHORT_ANSWER);
    }

    @Test
    void fallsBackToShortAnswerForUnknownOrNullInput() {
        assertThat(QuestionType.fromModelString("essay")).isEqualTo(QuestionType.SHORT_ANSWER);
        assertThat(QuestionType.fromModelString(null)).isEqualTo(QuestionType.SHORT_ANSWER);
        assertThat(QuestionType.fromModelString("")).isEqualTo(QuestionType.SHORT_ANSWER);
    }
}
