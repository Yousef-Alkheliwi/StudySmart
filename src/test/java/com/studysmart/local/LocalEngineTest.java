package com.studysmart.local;

import com.studysmart.domain.Chunk;
import com.studysmart.domain.DocumentStatus;
import com.studysmart.domain.QuestionType;
import com.studysmart.domain.QuizQuestion;
import com.studysmart.domain.StudyDocument;
import com.studysmart.embedding.HashingEmbeddingModel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The on-device engine end to end, driven by the deterministic hashing model. */
class LocalEngineTest {

    private static final String BIOLOGY =
            "Cellular respiration is the process by which cells break down glucose to produce ATP. "
                    + "It occurs in three stages: glycolysis, the Krebs cycle, and oxidative phosphorylation. "
                    + "Glycolysis takes place in the cytoplasm and does not require oxygen. "
                    + "Mitochondria are often called the powerhouse of the cell because they generate most of its ATP.";

    private static final String HISTORY =
            "The French Revolution began in 1789 with the storming of the Bastille. "
                    + "The National Assembly abolished feudal privileges in August of that year. "
                    + "Robespierre led the Committee of Public Safety during the Reign of Terror.";

    private final HashingEmbeddingModel model = new HashingEmbeddingModel();

    private static GroundedSource source(String docId, String filename, Integer page, String text) {
        StudyDocument doc = new StudyDocument(docId, "p1", filename, "text/plain", text.length(), null, "/x",
                DocumentStatus.READY, null, Instant.now());
        return new GroundedSource(new Chunk(docId + "-c", docId, "p1", 0, page, text, 0, text.length()), doc);
    }

    @Test
    void extractiveAnswerReturnsTheRelevantSentenceWithACitation() {
        List<GroundedSource> sources = List.of(
                source("d1", "bio.pdf", 3, BIOLOGY),
                source("d2", "history.pdf", 7, HISTORY));

        AnswerResult result = new ExtractiveAnswerEngine(model).answer("where does glycolysis take place?", sources);

        assertThat(result.text()).contains("Glycolysis takes place in the cytoplasm");
        assertThat(result.citations()).isNotEmpty();
        assertThat(result.citations().get(0).documentFilename()).isEqualTo("bio.pdf");
        assertThat(result.citations().get(0).page()).isEqualTo(3);
        assertThat(result.citations().get(0).quotedText()).contains("cytoplasm");
    }

    @Test
    void extractiveAnswerDeduplicatesSentencesSeenThroughOverlappingChunks() {
        // Overlapping chunks re-deliver the same sentence; it must appear once.
        List<GroundedSource> sources = List.of(source("d1", "bio.pdf", 1, BIOLOGY), source("d1", "bio.pdf", 1, BIOLOGY));

        AnswerResult result = new ExtractiveAnswerEngine(model).answer("what is cellular respiration?", sources);

        long occurrences = result.text().lines().filter(l -> l.contains("Cellular respiration is the process")).count();
        assertThat(occurrences).isEqualTo(1);
    }

    @Test
    void extractiveAnswerAdmitsWhenNothingIsRelevant() {
        AnswerResult result = new ExtractiveAnswerEngine(model)
                .answer("what is the capital of Australia?", List.of(source("d1", "bio.pdf", 1, BIOLOGY)));

        assertThat(result.text()).contains("couldn't find a clear answer");
        assertThat(result.citations()).hasSize(1);
    }

    @Test
    void summarizerGroupsByDocumentAndCitesEverySentence() {
        List<GroundedSource> sources = List.of(
                source("d1", "bio.pdf", 1, BIOLOGY),
                source("d2", "history.pdf", 2, HISTORY));

        AnswerResult result = new ExtractiveSummarizer(model).summarize(sources);

        assertThat(result.text()).contains("## bio.pdf").contains("## history.pdf");
        long bullets = result.text().lines().filter(l -> l.startsWith("- ")).count();
        assertThat(result.citations()).hasSize((int) bullets);
        assertThat(bullets).isGreaterThanOrEqualTo(4);
    }

    @Test
    void clozeQuizBlanksASalientTermAndSpreadsAcrossDocuments() {
        List<GroundedSource> sources = List.of(
                source("d1", "bio.pdf", 1, BIOLOGY),
                source("d2", "history.pdf", 2, HISTORY));

        List<QuizQuestion> questions = ClozeQuizGenerator.generate(sources, 4,
                List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.FLASHCARD, QuestionType.SHORT_ANSWER));

        assertThat(questions).hasSize(4);
        assertThat(questions).extracting(QuizQuestion::sourceFilename).contains("bio.pdf", "history.pdf");
        for (QuizQuestion q : questions) {
            assertThat(q.prompt()).contains("_____");
            assertThat(q.prompt().toLowerCase()).doesNotContain(q.answer().toLowerCase());
            assertThat(q.explanation()).containsIgnoringCase(q.answer());
            if (q.type() == QuestionType.MULTIPLE_CHOICE) {
                assertThat(q.choices()).hasSize(4).contains(q.answer()).doesNotHaveDuplicates();
            }
        }
        assertThat(questions).extracting(QuizQuestion::type)
                .containsExactly(QuestionType.MULTIPLE_CHOICE, QuestionType.FLASHCARD,
                        QuestionType.SHORT_ANSWER, QuestionType.MULTIPLE_CHOICE);
    }

    @Test
    void lexicalGraderAcceptsParaphraseAndRejectsWrongTerms() {
        assertThat(LexicalGrader.grade("the mitochondria", "Mitochondria").correct()).isTrue();
        assertThat(LexicalGrader.grade("oxidative phosphorylation", "it is oxidative phosphorylation in the cell").correct()).isTrue();
        GradeResult wrong = LexicalGrader.grade("glycolysis", "the Krebs cycle");
        assertThat(wrong.correct()).isFalse();
        assertThat(wrong.feedback()).contains("glycolysis");
        assertThat(LexicalGrader.grade("glycolysis", "   ").correct()).isFalse();
    }
}
