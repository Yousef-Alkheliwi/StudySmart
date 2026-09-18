package com.studysmart.eval;

import com.studysmart.domain.Chunk;
import com.studysmart.domain.DocumentStatus;
import com.studysmart.domain.StudyDocument;
import com.studysmart.ingest.ChunkCandidate;
import com.studysmart.ingest.Chunker;
import com.studysmart.ingest.ExtractedPage;
import com.studysmart.local.GroundedSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A small multi-subject study corpus and a question set with the fact each
 * question should surface. Shared by the deterministic regression tests and
 * the answer-quality evaluation.
 */
public final class EvalCorpus {

    /** A question, the substring the right answer must contain, and whether the material covers it at all. */
    public record Question(String text, String expected, boolean answerable) {
        static Question of(String text, String expected) {
            return new Question(text, expected, true);
        }

        static Question unanswerable(String text) {
            return new Question(text, "", false);
        }
    }

    public static final String BIOLOGY = """
            Cellular respiration is the process by which cells break down glucose to release energy as ATP.
            It occurs in three stages: glycolysis, the Krebs cycle, and oxidative phosphorylation.
            Glycolysis takes place in the cytoplasm and does not require oxygen.
            The Krebs cycle and oxidative phosphorylation happen inside the mitochondria and consume oxygen.

            Mitochondria are often called the powerhouse of the cell because they generate most of its ATP.
            Photosynthesis runs the opposite way: chloroplasts capture light energy and build glucose from carbon dioxide and water.
            Enzymes are proteins that speed up biochemical reactions by lowering the activation energy needed.
            Each enzyme has an active site shaped to fit one specific substrate, which makes enzymes highly selective.
            """;

    public static final String HISTORY = """
            The French Revolution began in 1789 when crowds stormed the Bastille prison in Paris.
            The National Assembly abolished feudal privileges in August of that same year.
            Maximilien Robespierre led the Committee of Public Safety during the Reign of Terror.
            Tens of thousands were executed before Robespierre himself was guillotined in 1794.

            The Declaration of the Rights of Man set out liberty, property and resistance to oppression as natural rights.
            Napoleon Bonaparte seized power in a coup in 1799, ending the revolutionary decade.
            """;

    public static final String ECONOMICS = """
            When supply exceeds demand at the current price, the price falls until the market clears.
            A shortage appears when demand exceeds supply, and competition among buyers pushes the price up.
            Inflation is a sustained rise in the general price level, which erodes the purchasing power of money.
            Central banks raise interest rates to cool inflation, because borrowing becomes more expensive and spending slows.

            Gross domestic product measures the total value of goods and services a country produces in a year.
            A recession is commonly defined as two consecutive quarters of falling gross domestic product.
            """;

    public static final List<Question> QUESTIONS = List.of(
            // Paraphrased questions: almost no word overlap with the answer sentence.
            Question.of("how do cells release energy from food?", "Cellular respiration"),
            Question.of("which part of the cell makes most of its energy?", "Mitochondria"),
            Question.of("what speeds up chemical reactions inside a cell?", "Enzymes"),
            Question.of("why is an enzyme only able to act on one molecule?", "active site"),
            Question.of("where does glycolysis take place?", "cytoplasm"),
            Question.of("how many stages does cellular respiration have?", "three stages"),
            Question.of("which process turns sunlight into sugar?", "Photosynthesis"),
            // History.
            Question.of("when did the French Revolution start?", "1789"),
            Question.of("who ran the Committee of Public Safety?", "Robespierre"),
            Question.of("what happened to feudal privileges?", "abolished"),
            Question.of("who took power at the end of the revolution?", "Napoleon"),
            // Economics.
            Question.of("what happens to prices when there is too much of a good?", "price falls"),
            Question.of("what does a central bank do to fight rising prices?", "interest rates"),
            Question.of("what counts as a recession?", "two consecutive quarters"),
            Question.of("what does GDP measure?", "Gross domestic product"),
            // Not covered by the material at all - the engine must decline.
            Question.unanswerable("what is the capital of Australia?"),
            Question.unanswerable("how do I bake sourdough bread at home?"));

    private EvalCorpus() {
    }

    /** Chunks the three documents the same way ingestion would, then wraps them as retrieval results. */
    public static List<GroundedSource> sources() {
        List<GroundedSource> out = new ArrayList<>();
        out.addAll(chunk("d-bio", "biology.txt", BIOLOGY));
        out.addAll(chunk("d-hist", "history.txt", HISTORY));
        out.addAll(chunk("d-econ", "economics.txt", ECONOMICS));
        return out;
    }

    private static List<GroundedSource> chunk(String documentId, String filename, String text) {
        StudyDocument doc = new StudyDocument(documentId, "eval", filename, "text/plain", text.length(), null,
                "/tmp/" + filename, DocumentStatus.READY, null, Instant.EPOCH);
        List<ChunkCandidate> candidates = new Chunker(60, 12).chunk(List.of(new ExtractedPage(1, text)));
        List<GroundedSource> out = new ArrayList<>();
        int ordinal = 0;
        for (ChunkCandidate c : candidates) {
            Chunk chunk = new Chunk(documentId + "-c" + ordinal, documentId, "eval", ordinal,
                    c.page(), c.content(), c.charStart(), c.charEnd());
            out.add(new GroundedSource(chunk, doc));
            ordinal++;
        }
        return out;
    }
}
