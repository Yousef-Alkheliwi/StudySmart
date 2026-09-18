package com.studysmart.local;

import com.studysmart.embedding.Stopwords;

import com.studysmart.domain.QuestionType;
import com.studysmart.domain.QuizQuestion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds quiz questions from the material itself, with no language model.
 *
 * <p>For every sentence it finds the single most <em>salient</em> term - the
 * word that is frequent in that sentence but rare across the rest of the
 * material (TF-IDF) - and blanks it out. A sentence whose key term is
 * "mitochondria" becomes a cloze flashcard, a fill-in-the-blank short
 * answer, or a multiple-choice item whose distractors are other documents'
 * key terms. Sentences shaped like definitions ("X is ...", "X refers to")
 * are preferred, and picks are spread round-robin across documents so a
 * quiz on three lectures doesn't come entirely from the first one.
 */
public final class ClozeQuizGenerator {

    /** Five letters or more: short words ("cell", "uses") make poor blanks even when statistically salient. */
    private static final Pattern WORD = Pattern.compile("\\b[\\p{L}][\\p{L}\\-]{4,}\\b");
    private static final Pattern DEFINITION = Pattern.compile(
            "\\b(is|are|refers to|is defined as|is called|means|consists of|describes)\\b", Pattern.CASE_INSENSITIVE);
    private static final int MIN_WORDS = 8;
    private static final int MAX_WORDS = 45;
    private static final int MAX_CANDIDATES = 800;
    private static final int DISTRACTORS = 3;

    private ClozeQuizGenerator() {
    }

    public static List<QuizQuestion> generate(List<GroundedSource> sources, int count, List<QuestionType> types) {
        List<SourceSentence> sentences = SentenceCorpus.from(sources, MAX_CANDIDATES);
        if (sentences.isEmpty()) {
            return List.of();
        }

        Map<String, Integer> documentFrequency = new HashMap<>();
        List<List<String>> tokensPerSentence = new ArrayList<>();
        for (SourceSentence s : sentences) {
            List<String> tokens = tokens(s.text());
            tokensPerSentence.add(tokens);
            for (String t : new java.util.HashSet<>(tokens)) {
                documentFrequency.merge(t, 1, Integer::sum);
            }
        }

        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            int wordCount = sentences.get(i).text().trim().split("\\s+").length;
            if (wordCount < MIN_WORDS || wordCount > MAX_WORDS) {
                continue;
            }
            List<String> tokens = tokensPerSentence.get(i);
            Candidate best = bestTerm(sentences.get(i), tokens, documentFrequency, sentences.size());
            if (best != null) {
                candidates.add(best);
            }
        }
        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Candidate> picked = pickAcrossDocuments(candidates, count);
        List<QuestionType> cycle = types.isEmpty()
                ? List.of(QuestionType.MULTIPLE_CHOICE, QuestionType.SHORT_ANSWER, QuestionType.FLASHCARD)
                : types;

        List<QuizQuestion> questions = new ArrayList<>();
        for (int i = 0; i < picked.size(); i++) {
            Candidate c = picked.get(i);
            QuestionType type = cycle.get(i % cycle.size());
            questions.add(toQuestion(c, type, candidates, i));
        }
        return questions;
    }

    private static Candidate bestTerm(SourceSentence sentence, List<String> tokens, Map<String, Integer> df, int total) {
        Map<String, Integer> tf = new HashMap<>();
        for (String t : tokens) {
            tf.merge(t, 1, Integer::sum);
        }
        String bestTerm = null;
        double bestScore = 0;
        for (Map.Entry<String, Integer> e : tf.entrySet()) {
            String term = e.getKey();
            if (Stopwords.isStopword(term)) {
                continue;
            }
            int frequency = df.getOrDefault(term, 1);
            // Terms in more than a quarter of sentences are theme words, not answers.
            if (frequency > Math.max(2, total / 4)) {
                continue;
            }
            double score = e.getValue() * Math.log((double) total / frequency) * (term.length() >= 6 ? 1.2 : 1.0);
            if (score > bestScore) {
                bestScore = score;
                bestTerm = term;
            }
        }
        if (bestTerm == null) {
            return null;
        }
        double sentenceScore = bestScore + (DEFINITION.matcher(sentence.text()).find() ? 2.0 : 0.0);
        return new Candidate(sentence, originalCasing(sentence.text(), bestTerm), sentenceScore);
    }

    /** Round-robin over documents, best-scored candidate first within each. */
    private static List<Candidate> pickAcrossDocuments(List<Candidate> candidates, int count) {
        Map<String, List<Candidate>> byDocument = new LinkedHashMap<>();
        for (Candidate c : candidates) {
            byDocument.computeIfAbsent(c.sentence.source().document().id(), k -> new ArrayList<>()).add(c);
        }
        for (List<Candidate> list : byDocument.values()) {
            list.sort((a, b) -> Double.compare(b.score, a.score));
        }
        List<Candidate> picked = new ArrayList<>();
        java.util.Set<String> usedTerms = new java.util.HashSet<>();
        int round = 0;
        while (picked.size() < count) {
            boolean anyLeft = false;
            for (List<Candidate> list : byDocument.values()) {
                if (round < list.size()) {
                    anyLeft = true;
                    Candidate c = list.get(round);
                    if (usedTerms.add(c.term.toLowerCase(Locale.ROOT)) && picked.size() < count) {
                        picked.add(c);
                    }
                }
            }
            if (!anyLeft) {
                break;
            }
            round++;
        }
        return picked;
    }

    private static QuizQuestion toQuestion(Candidate c, QuestionType type, List<Candidate> pool, int ordinal) {
        String blanked = blank(c.sentence.text(), c.term);
        GroundedSource source = c.sentence.source();
        String explanation = "From the material: “" + c.sentence.text() + "”";

        return switch (type) {
            case FLASHCARD -> new QuizQuestion(null, null, ordinal, QuestionType.FLASHCARD,
                    blanked, List.of(), c.term, explanation,
                    source.document().id(), source.document().filename(), source.chunk().page());
            case SHORT_ANSWER -> new QuizQuestion(null, null, ordinal, QuestionType.SHORT_ANSWER,
                    "Fill in the missing term: " + blanked, List.of(), c.term, explanation,
                    source.document().id(), source.document().filename(), source.chunk().page());
            case MULTIPLE_CHOICE -> new QuizQuestion(null, null, ordinal, QuestionType.MULTIPLE_CHOICE,
                    "Which term completes this sentence? " + blanked, choices(c, pool), c.term, explanation,
                    source.document().id(), source.document().filename(), source.chunk().page());
        };
    }

    private static List<String> choices(Candidate correct, List<Candidate> pool) {
        List<String> distractors = new ArrayList<>();
        // Prefer terms from other sentences of similar length: plausible, but wrong.
        List<Candidate> others = new ArrayList<>(pool);
        others.removeIf(o -> o.term.equalsIgnoreCase(correct.term));
        others.sort((a, b) -> Integer.compare(
                Math.abs(a.term.length() - correct.term.length()),
                Math.abs(b.term.length() - correct.term.length())));
        for (Candidate o : others) {
            String t = o.term;
            if (distractors.stream().noneMatch(d -> d.equalsIgnoreCase(t))) {
                distractors.add(t);
            }
            if (distractors.size() == DISTRACTORS) {
                break;
            }
        }
        List<String> choices = new ArrayList<>(distractors);
        choices.add(correct.term);
        // Deterministic shuffle keyed on the sentence, so the same quiz reproduces the same order.
        java.util.Collections.shuffle(choices, new Random(correct.sentence.identity().hashCode()));
        return choices;
    }

    private static String blank(String sentence, String term) {
        return Pattern.compile("\\b" + Pattern.quote(term) + "\\b", Pattern.CASE_INSENSITIVE)
                .matcher(sentence).replaceFirst("_____");
    }

    private static String originalCasing(String sentence, String lowerTerm) {
        Matcher m = Pattern.compile("\\b" + Pattern.quote(lowerTerm) + "\\b", Pattern.CASE_INSENSITIVE).matcher(sentence);
        return m.find() ? m.group() : lowerTerm;
    }

    private static List<String> tokens(String text) {
        List<String> out = new ArrayList<>();
        Matcher m = WORD.matcher(text);
        while (m.find()) {
            out.add(m.group().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private record Candidate(SourceSentence sentence, String term, double score) {
    }
}
