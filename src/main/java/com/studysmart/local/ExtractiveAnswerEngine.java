package com.studysmart.local;

import com.studysmart.domain.Citation;
import com.studysmart.embedding.EmbeddingModel;
import com.studysmart.embedding.HashingEmbeddingModel;
import com.studysmart.embedding.Stopwords;
import com.studysmart.embedding.Vectors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Answers a question from course material without any language model: it
 * finds the sentences that are semantically closest to the question,
 * removes near-duplicates, and returns them verbatim with a citation for
 * each. It can never invent a fact, because every line of the answer is a
 * sentence that exists in the uploaded documents.
 *
 * <p>Three rules keep the answer tight rather than merely topical. A
 * sentence must clear an absolute relevance floor, so an unrelated question
 * gets an honest "not in your material" instead of the least-bad paragraph.
 * It must also stay close to the <em>best</em> sentence found, so one strong
 * answer isn't padded out with three vaguely related neighbours. And the
 * best sentence is printed first, with any supporting ones following in the
 * order they appear in the document, so the answer reads as prose.
 */
public final class ExtractiveAnswerEngine {

    private static final int MAX_SENTENCES = 4;
    private static final int MAX_CANDIDATES = 400;
    private static final float DUPLICATE_THRESHOLD = 0.92f;

    /**
     * A supporting sentence has to score at least this fraction of the best
     * sentence's score. Tuned on the evaluation set: lower pads answers with
     * loosely-related material, higher drops genuine second facts.
     */
    private static final float RELATIVE_CUTOFF = 0.72f;

    private final EmbeddingModel model;

    public ExtractiveAnswerEngine(EmbeddingModel model) {
        this.model = model;
    }

    public AnswerResult answer(String question, List<GroundedSource> sources) {
        List<SourceSentence> candidates = SentenceCorpus.from(sources, MAX_CANDIDATES);
        if (candidates.isEmpty()) {
            return new AnswerResult(
                    "I couldn't find any readable sentences in the retrieved material to answer from.", List.of());
        }

        float[] q = model.embed(question);
        List<float[]> vectors = model.embedAll(candidates.stream().map(SourceSentence::searchableText).toList());
        Set<String> questionTerms = contentTerms(question);

        List<Scored> scored = new ArrayList<>(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            SourceSentence sentence = candidates.get(i);
            float semantic = Vectors.dot(q, vectors.get(i));
            float lexical = overlap(questionTerms, contentTerms(sentence.searchableText()));
            // Mostly semantic, with a small lexical nudge so an exact term
            // match ("Krebs") wins ties over a merely-related sentence.
            scored.add(new Scored(sentence, vectors.get(i), 0.85f * semantic + 0.15f * lexical));
        }
        scored.sort((a, b) -> Float.compare(b.score, a.score));

        Scored best = scored.get(0);
        if (best.score < model.relevanceFloor()) {
            return new AnswerResult(
                    "I couldn't find a clear answer to that in this project's documents. The closest passage is:\n\n"
                            + "“" + best.sentence.displayText() + "”\n\n"
                            + "Try rephrasing, or upload material that covers it.",
                    List.of(best.sentence.toCitation(0)));
        }

        float supportFloor = Math.max(model.relevanceFloor(), best.score * RELATIVE_CUTOFF);
        List<Scored> chosen = new ArrayList<>();
        chosen.add(best);
        for (Scored s : scored.subList(1, scored.size())) {
            if (s.score < supportFloor || chosen.size() == MAX_SENTENCES) {
                break;
            }
            if (!isRedundant(s, chosen)) {
                chosen.add(s);
            }
        }

        // The direct answer leads; whatever supports it follows in reading order.
        List<Scored> ordered = new ArrayList<>();
        ordered.add(best);
        chosen.stream()
                .filter(s -> s != best)
                .sorted(Comparator.comparingInt(s -> s.sentence.position()))
                .forEach(ordered::add);

        StringBuilder text = new StringBuilder("Here is what your material says about that:\n");
        List<Citation> citations = new ArrayList<>();
        for (Scored s : ordered) {
            int order = citations.size();
            text.append("\n- ").append(s.sentence.displayText()).append(" [").append(order + 1).append("]");
            citations.add(s.sentence.toCitation(order));
        }
        return new AnswerResult(text.toString(), citations);
    }

    /**
     * True when a sentence says what an already-chosen one says: either its
     * vector is nearly identical, or the text is already on screen because a
     * chosen sentence pulled it in as its antecedent.
     */
    private static boolean isRedundant(Scored candidate, List<Scored> chosen) {
        for (Scored c : chosen) {
            if (Vectors.dot(c.vector, candidate.vector) > DUPLICATE_THRESHOLD) {
                return true;
            }
            if (c.sentence.displayText().contains(candidate.sentence.text())
                    || candidate.sentence.displayText().contains(c.sentence.text())) {
                return true;
            }
        }
        return false;
    }

    static Set<String> contentTerms(String text) {
        Set<String> terms = new HashSet<>();
        for (String token : HashingEmbeddingModel.tokenize(text)) {
            if (token.length() > 2 && !Stopwords.isStopword(token)) {
                terms.add(token);
            }
        }
        return terms;
    }

    private static float overlap(Set<String> questionTerms, Set<String> sentenceTerms) {
        if (questionTerms.isEmpty()) {
            return 0f;
        }
        int hits = 0;
        for (String t : questionTerms) {
            if (sentenceTerms.contains(t)) {
                hits++;
            }
        }
        return (float) hits / questionTerms.size();
    }

    private record Scored(SourceSentence sentence, float[] vector, float score) {
    }
}
