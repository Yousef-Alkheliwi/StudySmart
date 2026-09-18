package com.studysmart.domain;

/**
 * A grounding link between a piece of generated text and the exact source
 * material it came from: {@code quotedText} is the sentence the engine
 * actually used, and document/page say where it lives.
 */
public record Citation(
        int order,
        String documentId,
        String documentFilename,
        String chunkId,
        Integer page,
        String quotedText
) {
}
