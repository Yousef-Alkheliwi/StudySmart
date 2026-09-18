package com.studysmart.ingest;

/**
 * Raw text extracted from one page of a source document. {@code pageNumber}
 * is {@code null} for formats without pages (plain text, Markdown).
 */
public record ExtractedPage(Integer pageNumber, String text) {
}
