package com.studysmart.local;

import com.studysmart.domain.Chunk;
import com.studysmart.domain.DocumentStatus;
import com.studysmart.domain.QuestionType;
import com.studysmart.domain.QuizQuestion;
import com.studysmart.domain.StudyDocument;
import com.studysmart.embedding.HashingEmbeddingModel;
import com.studysmart.ingest.ChunkCandidate;
import com.studysmart.ingest.Chunker;
import com.studysmart.ingest.ExtractedPage;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A real lecture handout: a page of course admin, then the material. Quizzes
 * and summaries must come from the material.
 */
class QuizContentSelectionTest {

    private static final String HANDOUT = """
            BIO 101: Introductory Cell Biology
            Instructor: Dr. Amina Farouk
            Email: a.farouk@university.edu
            Office hours: Tuesdays 2:00 pm in Room 4.21
            Course code: BIO 101, 3 credits, Autumn semester
            Textbook: Alberts, Molecular Biology of the Cell, 7th edition.
            Assessment: coursework is worth 40% of your grade and the final exam 60%.

            Welcome to Introductory Cell Biology. In this chapter we will examine how cells
            release energy. By the end of this lecture you will be able to describe glycolysis.

            Cellular respiration breaks down glucose to release energy as ATP.
            Glycolysis takes place in the cytoplasm and does not require oxygen.
            Mitochondria generate most of the cell's ATP through oxidative phosphorylation.
            Enzymes lower the activation energy that a biochemical reaction needs to proceed.
            Chloroplasts capture light energy and build glucose from carbon dioxide and water.

            Further reading is listed at the end of the module handbook.
            Assignment 2 is due on 14 March. Copyright 2026 University of Somewhere.
            """;

    private static final List<String> BOILERPLATE_GIVEAWAYS = List.of(
            "Farouk", "farouk", "Office hours", "Room 4.21", "credits", "Textbook", "Alberts",
            "worth 40", "Welcome to", "in this chapter", "In this chapter", "By the end of",
            "Further reading", "due on", "Copyright", "university.edu");

    private static List<GroundedSource> handout() {
        StudyDocument doc = new StudyDocument("d1", "p1", "bio101-week1.pdf", "application/pdf",
                HANDOUT.length(), 1, "/x", DocumentStatus.READY, null, Instant.EPOCH);
        List<ChunkCandidate> candidates = new Chunker(60, 12).chunk(List.of(new ExtractedPage(1, HANDOUT)));
        List<GroundedSource> sources = new ArrayList<>();
        int ordinal = 0;
        for (ChunkCandidate c : candidates) {
            sources.add(new GroundedSource(
                    new Chunk("c" + ordinal, "d1", "p1", ordinal, 1, c.content(), c.charStart(), c.charEnd()), doc));
            ordinal++;
        }
        return sources;
    }

    @Test
    void neverQuizzesOnCourseAdminOrChapterIntroductions() {
        List<QuizQuestion> questions = ClozeQuizGenerator.generate(handout(), 6,
                List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.SHORT_ANSWER, QuestionType.FLASHCARD));

        assertThat(questions).isNotEmpty();
        for (QuizQuestion q : questions) {
            for (String giveaway : BOILERPLATE_GIVEAWAYS) {
                assertThat(q.prompt()).doesNotContain(giveaway);
                assertThat(q.explanation()).doesNotContain(giveaway);
            }
        }
    }

    @Test
    void quizzesOnTheSubjectMatterInstead() {
        List<QuizQuestion> questions = ClozeQuizGenerator.generate(handout(), 5, List.of(QuestionType.SHORT_ANSWER));

        String everything = questions.stream().map(q -> q.prompt() + " " + q.explanation()).reduce("", String::concat);
        assertThat(everything).containsAnyOf("glucose", "Glycolysis", "cytoplasm", "Mitochondria", "Enzymes", "Chloroplasts");
    }

    @Test
    void doesNotBlankOutFillerWords() {
        List<QuizQuestion> questions = ClozeQuizGenerator.generate(handout(), 6, List.of(QuestionType.SHORT_ANSWER));

        assertThat(questions).extracting(q -> q.answer().toLowerCase())
                .doesNotContain("process", "important", "different", "example", "section", "chapter");
    }

    @Test
    void blanksTheConceptBeingDefinedRatherThanTheVerb() {
        List<QuizQuestion> questions = ClozeQuizGenerator.generate(handout(), 6, List.of(QuestionType.SHORT_ANSWER));

        // Whatever it picks, it must not be testing the verb of the sentence.
        assertThat(questions).extracting(q -> q.answer().toLowerCase())
                .doesNotContain("generate", "release", "breaks", "takes", "lower", "capture", "needs");
    }

    @Test
    void prefersTheHeadOfACompoundTermOverItsModifier() {
        StudyDocument doc = new StudyDocument("d3", "p1", "terms.txt", "text/plain", 300, null, "/x",
                DocumentStatus.READY, null, Instant.EPOCH);
        String text = "Cellular respiration is the pathway that converts glucose into usable chemical energy. "
                + "Oxidative phosphorylation produces the majority of the cell's adenosine triphosphate.";
        List<GroundedSource> sources = List.of(new GroundedSource(
                new Chunk("c0", "d3", "p1", 0, 1, text, 0, text.length()), doc));

        List<QuizQuestion> questions = ClozeQuizGenerator.generate(sources, 2, List.of(QuestionType.SHORT_ANSWER));

        // "Cellular respiration" should be tested on "respiration", not "Cellular".
        assertThat(questions).extracting(q -> q.answer().toLowerCase())
                .doesNotContain("cellular", "oxidative");
    }

    @Test
    void summariesSkipTheCourseAdminToo() {
        AnswerResult summary = new ExtractiveSummarizer(new HashingEmbeddingModel()).summarize(handout());

        for (String giveaway : List.of("Farouk", "Office hours", "Textbook", "Copyright", "Welcome to")) {
            assertThat(summary.text()).doesNotContain(giveaway);
        }
        assertThat(summary.text()).containsAnyOf("Glycolysis", "Mitochondria", "Enzymes", "respiration");
    }

    @Test
    void stillProducesAQuizWhenADocumentIsNothingButAdmin() {
        StudyDocument doc = new StudyDocument("d2", "p1", "syllabus.pdf", "application/pdf", 200, 1, "/x",
                DocumentStatus.READY, null, Instant.EPOCH);
        String adminOnly = "Instructor: Dr. Amina Farouk teaches this course every autumn semester. "
                + "Office hours are on Tuesday afternoons in the biology building annexe.";
        List<GroundedSource> sources = List.of(new GroundedSource(
                new Chunk("c0", "d2", "p1", 0, 1, adminOnly, 0, adminOnly.length()), doc));

        // Better to quiz on something than to hand back nothing at all.
        assertThat(ClozeQuizGenerator.generate(sources, 2, List.of(QuestionType.SHORT_ANSWER))).isNotEmpty();
    }
}
