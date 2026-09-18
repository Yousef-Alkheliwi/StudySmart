package com.studysmart.local;

import com.studysmart.domain.Citation;

import java.util.Locale;
import java.util.Set;

/**
 * One sentence of course material, still attached to the chunk and document
 * it came from.
 *
 * <p>{@code previous} is the sentence immediately before it in the same
 * chunk, when there is one. It matters because a sentence like "It occurs in
 * three stages..." is meaningless on its own: the topic lives in the
 * sentence before. {@link #searchableText()} and {@link #displayText()} pull
 * that antecedent back in for exactly those cases, so such facts are both
 * findable and readable.
 */
public record SourceSentence(String text, String previous, GroundedSource source, int position) {

    /**
     * Words that point at something named earlier. A sentence starting with
     * one of these cannot be understood alone.
     */
    private static final Set<String> DANGLING_STARTERS = Set.of(
            "it", "its", "this", "that", "these", "those", "they", "them", "their",
            "he", "she", "his", "her", "him", "such", "both", "each");

    public SourceSentence(String text, GroundedSource source, int position) {
        this(text, null, source, position);
    }

    /** True when the sentence opens with a reference to something named in the sentence before. */
    public boolean needsAntecedent() {
        if (previous == null || previous.isBlank()) {
            return false;
        }
        String firstWord = text.split("[^\\p{L}]+", 2)[0].toLowerCase(Locale.ROOT);
        return DANGLING_STARTERS.contains(firstWord);
    }

    /** What the engine embeds and matches against: the antecedent is included so the sentence's topic is visible. */
    public String searchableText() {
        return needsAntecedent() ? previous + " " + text : text;
    }

    /** What the reader sees and what gets cited - identical to the source material, just not cut off mid-thought. */
    public String displayText() {
        return needsAntecedent() ? previous + " " + text : text;
    }

    public Citation toCitation(int order) {
        return new Citation(order, source.document().id(), source.document().filename(),
                source.chunk().id(), source.chunk().page(), displayText());
    }

    /** Whitespace/case-insensitive identity, so the same sentence seen through two overlapping chunks counts once. */
    public String identity() {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
