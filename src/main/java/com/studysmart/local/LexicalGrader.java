package com.studysmart.local;

import com.studysmart.embedding.Stopwords;


import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Grades a short answer without a language model: normalizes both texts,
 * accepts a containment match in either direction (the reference answer
 * inside a fuller student answer, or a terse student answer that is the
 * whole reference), and otherwise falls back to token-set overlap. Strict
 * enough to reject wrong terms, lenient on phrasing and articles.
 */
public final class LexicalGrader {

    private static final float OVERLAP_THRESHOLD = 0.6f;

    private LexicalGrader() {
    }

    public static GradeResult grade(String referenceAnswer, String studentAnswer) {
        String reference = normalize(referenceAnswer);
        String student = normalize(studentAnswer);
        if (student.isBlank()) {
            return new GradeResult(false, "No answer was given. The expected answer was: " + referenceAnswer + ".");
        }

        if (student.equals(reference) || student.contains(reference)
                || (reference.contains(student) && student.length() >= Math.max(3, reference.length() / 2))) {
            return new GradeResult(true, "Correct - that matches the material.");
        }

        Set<String> refTerms = terms(reference);
        Set<String> studentTerms = terms(student);
        if (refTerms.isEmpty()) {
            return new GradeResult(false, "Not quite. The expected answer was: " + referenceAnswer + ".");
        }
        int shared = 0;
        for (String t : refTerms) {
            if (studentTerms.contains(t)) {
                shared++;
            }
        }
        float overlap = (float) shared / refTerms.size();
        if (overlap >= OVERLAP_THRESHOLD) {
            return new GradeResult(true, "Correct - you covered the key points (" + referenceAnswer + ").");
        }
        if (overlap > 0) {
            return new GradeResult(false, "Partly there, but the key term was missing. Expected: " + referenceAnswer + ".");
        }
        return new GradeResult(false, "Not quite. The expected answer was: " + referenceAnswer + ".");
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}\\s]", " ")
                .replaceAll("\\b(the|a|an|is|are|of|to|and)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Set<String> terms(String normalized) {
        Set<String> out = new HashSet<>();
        for (String t : normalized.split(" ")) {
            if (t.length() > 2 && !Stopwords.isStopword(t)) {
                out.add(t);
            }
        }
        return out;
    }
}
