package com.studysmart.ingest;

/** A chunk produced by {@link Chunker}, not yet assigned an id or persisted. */
public record ChunkCandidate(Integer page, String content, int charStart, int charEnd) {
}
