package com.studysmart.domain;

/**
 * A retrievable slice of a document: a few hundred words, tagged with the
 * page it came from (PDFs) so answers can cite back to an exact location.
 */
public record Chunk(
        String id,
        String documentId,
        String projectId,
        int ordinal,
        Integer page,
        String content,
        int charStart,
        int charEnd
) {
}
