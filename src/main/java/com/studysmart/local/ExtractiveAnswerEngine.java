package com.studysmart.local;

import com.studysmart.embedding.Stopwords;

import com.studysmart.domain.Citation;
import com.studysmart.embedding.EmbeddingModel;
import com.studysmart.embedding.HashingEmbeddingModel;
import com.studysmart.embedding.Vectors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Answers a question from course material without any language model: it
 * finds the sentences that are semantically closest to the question,
 * removes near-duplicates, and returns them verbatim with a citation for
 * each. It can never invent a fact, because every line of the answer is a
 * sentence that exists in the uploaded documents.
 */
public final class ExtractiveAnswerEngine {

    private static final int MAX_SENTENCES = 4;
    private static final int MAX_CANDIDATES = 300;
    private static final float DUPLICATE_THRESHOLD = 0.92f;

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
        List<float[]> vectors = model.embedAll(candidates.stream().map(SourceSentence::text).toList());
        Set<String> questionTerms = contentTerms(question);

        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            float semantic = Vectors.dot(q, vectors.get(i));
            float lexical = overlap(questionTerms, contentTerms(candidates.get(i).text()));
            // Mostly semantic, with a small lexical nudge so an exact term
            // match ("Krebs") wins ties over a merely-related sentence.
            scored.add(new Scored(candidates.get(i), vectors.get(i), 0.85f * semantic + 0.15f * lexical));
        }
        scored.sort((a, b) -> Float.compare(b.score, a.score));

        List<Scored> chosen = new ArrayList<>();
        for (Scored s : scored) {
            if (s.score < model.relevanceFloor()) {
                break;
            }
            boolean duplicate = chosen.stream().anyMatch(c -> Vectors.dot(c.vector, s.vector) > DUPLICATE_THRESHOLD);
            if (!duplicate) {
                chosen.add(s);
            }
            if (chosen.size() == MAX_SENTENCES) {
                break;
            }
        }

        if (chosen.isEmpty()) {
            Scored best = scored.get(0);
            return new AnswerResult(
                    "I couldn't find a clear answer to that in this project's documents. The closest passage is:\n\n"
                            + "“" + best.sentence.text() + "”\n\n"
                            + "Try rephrasing, or upload material that covers it.",
                    List.of(best.sentence.toCitation(0)));
        }

        StringBuilder text = new StringBuilder("Here is what your material says about that:\n");
        List<Citation> citations = new ArrayList<>();
        for (Scored s : chosen) {
            int order = citations.size();
            text.append("\n- ").append(s.sentence.text()).append(" [").append(order + 1).append("]");
            citations.add(s.sentence.toCitation(order));
        }
        return new AnswerResult(text.toString(), citations);
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
