package com.studysmart.eval;

import com.studysmart.embedding.EmbeddingModel;
import com.studysmart.embedding.LocalOnnxEmbeddingModel;
import com.studysmart.eval.EvalCorpus.Question;
import com.studysmart.local.AnswerResult;
import com.studysmart.local.ExtractiveAnswerEngine;
import com.studysmart.local.GroundedSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;
import java.util.List;

/**
 * Measures answer quality against the real on-device embedding model, so
 * changes to the engine can be compared instead of guessed at. Excluded from
 * the normal test run (it needs the ~90 MB model); run it with:
 *
 * <pre>./mvnw test -Dstudysmart.eval=true -Dtest=AnswerQualityEval</pre>
 */
@EnabledIfSystemProperty(named = "studysmart.eval", matches = "true")
class AnswerQualityEval {

    @Test
    void report() throws Exception {
        EmbeddingModel model = LocalOnnxEmbeddingModel.load(Path.of("data/models"));
        List<GroundedSource> sources = EvalCorpus.sources();
        ExtractiveAnswerEngine engine = new ExtractiveAnswerEngine(model);

        int answerable = 0, top1 = 0, anyHit = 0, declinedWrongly = 0;
        int unanswerable = 0, declinedCorrectly = 0;
        int totalSentences = 0;

        System.out.println("\n================ ANSWER QUALITY EVAL ================");
        for (Question q : EvalCorpus.QUESTIONS) {
            AnswerResult result = engine.answer(q.text(), sources);
            List<String> bullets = result.text().lines()
                    .filter(l -> l.startsWith("- "))
                    .map(l -> l.substring(2))
                    .toList();
            boolean declined = bullets.isEmpty();

            if (q.answerable()) {
                answerable++;
                totalSentences += bullets.size();
                boolean inFirst = !bullets.isEmpty() && bullets.get(0).contains(q.expected());
                boolean anywhere = bullets.stream().anyMatch(b -> b.contains(q.expected()));
                if (inFirst) top1++;
                if (anywhere) anyHit++;
                if (declined) declinedWrongly++;
                System.out.printf("%-4s %-58s -> %s%n",
                        inFirst ? "TOP1" : anywhere ? "hit" : declined ? "MISS(declined)" : "MISS",
                        q.text(),
                        bullets.isEmpty() ? "(declined)" : truncate(bullets.get(0)));
            } else {
                unanswerable++;
                if (declined) {
                    declinedCorrectly++;
                    System.out.printf("%-4s %-58s -> (declined, correct)%n", "OK", q.text());
                } else {
                    System.out.printf("%-4s %-58s -> %s%n", "FP", q.text(), truncate(bullets.get(0)));
                }
            }
        }

        System.out.println("-----------------------------------------------------");
        System.out.printf("answerable questions   : %d%n", answerable);
        System.out.printf("top-1 correct          : %d/%d (%.0f%%)%n", top1, answerable, 100.0 * top1 / answerable);
        System.out.printf("correct fact anywhere  : %d/%d (%.0f%%)%n", anyHit, answerable, 100.0 * anyHit / answerable);
        System.out.printf("wrongly declined       : %d%n", declinedWrongly);
        System.out.printf("avg sentences returned : %.2f%n", (double) totalSentences / answerable);
        System.out.printf("out-of-scope declined  : %d/%d%n", declinedCorrectly, unanswerable);
        System.out.println("=====================================================\n");
    }

    private static String truncate(String s) {
        return s.length() <= 70 ? s : s.substring(0, 67) + "...";
    }
}
