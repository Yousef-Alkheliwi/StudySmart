package com.studysmart.local;

import com.studysmart.domain.Chunk;
import com.studysmart.domain.DocumentStatus;
import com.studysmart.domain.QuestionType;
import com.studysmart.domain.QuizQuestion;
import com.studysmart.domain.StudyDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lecture slides, as PDF extraction actually delivers them: one line per
 * title or bullet, slide numbers on their own line, bullet glyphs, and
 * hardly a full stop anywhere. Flattening that into one blob used to turn a
 * whole slide into a single "sentence", which then became a quiz question.
 */
class SlideDeckSplittingTest {

    private static final String SLIDES = """
            CPSC 322: \s
            Introduction to \s
            Artificial Intelligence
            Cristina Conati
            1

            Outline
            2
            • Representation and Reasoning: Dimensions
            • An Overview of This Course

            Representation and Reasoning
            3
            To use these inputs an agent needs to represent and reason
            about them
            • Acquire and represent knowledge about a domain
            • Use the knowledge to solve problems in that domain
            """;

    private static GroundedSource slide(String text) {
        StudyDocument doc = new StudyDocument("d1", "p1", "lecture.pdf", "application/pdf", text.length(), 3, "/x",
                DocumentStatus.READY, null, Instant.EPOCH);
        return new GroundedSource(new Chunk("c1", "d1", "p1", 0, 1, text, 0, text.length()), doc);
    }

    @Test
    void keepsEachBulletSeparateInsteadOfMergingTheSlide() {
        List<String> sentences = SentenceSplitter.split(SLIDES);

        assertThat(sentences).contains(
                "Acquire and represent knowledge about a domain",
                "Use the knowledge to solve problems in that domain");
        // No line may swallow a neighbouring bullet.
        assertThat(sentences).noneMatch(s -> s.contains("domain") && s.contains("Outline"));
        assertThat(sentences).allSatisfy(s -> assertThat(s.split("\\s+").length).isLessThan(30));
    }

    @Test
    void stripsBulletGlyphsAndSlideNumbers() {
        List<String> sentences = SentenceSplitter.split(SLIDES);

        assertThat(sentences).noneMatch(s -> s.startsWith("•"));
        assertThat(sentences).noneMatch(s -> s.matches("\\d+"));
        assertThat(sentences).noneMatch(s -> s.contains(" 2 Representation"));
    }

    @Test
    void rejoinsALineThatWrappedMidSentence() {
        List<String> sentences = SentenceSplitter.split(SLIDES);

        assertThat(sentences).contains("To use these inputs an agent needs to represent and reason about them");
    }

    @Test
    void buildsQuizQuestionsFromBulletsNotFromWholeSlides() {
        List<QuizQuestion> questions = ClozeQuizGenerator.generate(List.of(slide(SLIDES)), 3,
                List.of(QuestionType.SHORT_ANSWER));

        assertThat(questions).isNotEmpty();
        for (QuizQuestion q : questions) {
            // A blob question would be enormous and would carry slide furniture.
            assertThat(q.prompt().split("\\s+").length).isLessThan(35);
            assertThat(q.prompt()).doesNotContain("Cristina Conati").doesNotContain("CPSC 322");
        }
    }

    @Test
    void doesNotBuildAQuestionFromAHalfFinishedBullet() {
        String cut = "Deterministic vs Stochastic\nYou can enumerate the states of the world or\n";
        List<QuizQuestion> questions = ClozeQuizGenerator.generate(List.of(slide(cut)), 2,
                List.of(QuestionType.SHORT_ANSWER));

        assertThat(questions).noneMatch(q -> q.prompt().trim().endsWith("or"));
    }

    @Test
    void stillHandlesOrdinaryProseWithHardWrapping() {
        String prose = """
                Cellular respiration is the process by which cells break down
                glucose to release energy as ATP. Glycolysis takes place in the
                cytoplasm and does not require oxygen.
                """;

        assertThat(SentenceSplitter.split(prose)).containsExactly(
                "Cellular respiration is the process by which cells break down glucose to release energy as ATP.",
                "Glycolysis takes place in the cytoplasm and does not require oxygen.");
    }
}
