package com.studysmart.local;

import com.studysmart.domain.Citation;
import com.studysmart.embedding.EmbeddingModel;
import com.studysmart.embedding.Vectors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Summarizes documents by selecting their most central sentences.
 *
 * <p>Each sentence is embedded; the mean of all vectors is the "topic
 * centroid" of the material. Sentences are then picked by Maximal Marginal
 * Relevance - closeness to the centroid, penalized by similarity to
 * sentences already chosen - so the summary covers the material's main
 * threads instead of restating its single biggest one four times. Output is
 * grouped by document, in reading order.
 */
public final class ExtractiveSummarizer {

    private static final int MAX_CANDIDATES = 600;
    private static final float LAMBDA = 0.7f;

    private final EmbeddingModel model;

    public ExtractiveSummarizer(EmbeddingModel model) {
        this.model = model;
    }

    /** A study summary is about the subject, so course admin and chapter intros are left out - unless that is all there is. */
    private static List<SourceSentence> summarizable(List<SourceSentence> sentences) {
        List<SourceSentence> content = sentences.stream()
                .filter(s -> StudyContentFilter.isTestableContent(s.text()))
                .toList();
        return content.isEmpty() ? sentences : content;
    }

    public AnswerResult summarize(List<GroundedSource> sources) {
        List<SourceSentence> sentences = summarizable(SentenceCorpus.from(sources, MAX_CANDIDATES));
        if (sentences.isEmpty()) {
            return new AnswerResult("The selected documents contain no readable sentences to summarize.", List.of());
        }

        List<float[]> vectors = model.embedAll(sentences.stream().map(SourceSentence::searchableText).toList());
        float[] centroid = Vectors.mean(vectors);
        int target = Math.max(4, Math.min(12, sentences.size() / 8));

        List<Integer> chosen = new ArrayList<>();
        boolean[] taken = new boolean[sentences.size()];
        while (chosen.size() < target && chosen.size() < sentences.size()) {
            int bestIndex = -1;
            float bestScore = -Float.MAX_VALUE;
            for (int i = 0; i < sentences.size(); i++) {
                if (taken[i]) {
                    continue;
                }
                float relevance = Vectors.dot(centroid, vectors.get(i));
                float redundancy = 0f;
                for (int j : chosen) {
                    redundancy = Math.max(redundancy, Vectors.dot(vectors.get(i), vectors.get(j)));
                }
                float score = LAMBDA * relevance - (1 - LAMBDA) * redundancy;
                if (score > bestScore) {
                    bestScore = score;
                    bestIndex = i;
                }
            }
            taken[bestIndex] = true;
            chosen.add(bestIndex);
        }

        chosen.sort(Comparator.comparingInt(i -> sentences.get(i).position()));

        Map<String, List<SourceSentence>> byDocument = new LinkedHashMap<>();
        for (int i : chosen) {
            SourceSentence s = sentences.get(i);
            byDocument.computeIfAbsent(s.source().document().filename(), k -> new ArrayList<>()).add(s);
        }

        StringBuilder text = new StringBuilder();
        List<Citation> citations = new ArrayList<>();
        for (Map.Entry<String, List<SourceSentence>> entry : byDocument.entrySet()) {
            text.append("## ").append(entry.getKey()).append("\n");
            for (SourceSentence s : entry.getValue()) {
                int order = citations.size();
                text.append("- ").append(s.displayText()).append(" [").append(order + 1).append("]\n");
                citations.add(s.toCitation(order));
            }
            text.append("\n");
        }
        return new AnswerResult(text.toString().trim(), citations);
    }
}
