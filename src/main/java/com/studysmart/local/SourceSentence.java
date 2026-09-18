package com.studysmart.local;

import com.studysmart.domain.Citation;

/** One sentence of course material, still attached to the chunk and document it came from. */
public record SourceSentence(String text, GroundedSource source, int position) {

    public Citation toCitation(int order) {
        return new Citation(order, source.document().id(), source.document().filename(),
                source.chunk().id(), source.chunk().page(), text);
    }

    /** Whitespace/case-insensitive identity, so the same sentence seen through two overlapping chunks counts once. */
    public String identity() {
        return text.toLowerCase().replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
