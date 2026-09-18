package com.studysmart.ingest;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Handles plain text and Markdown notes, which have no page structure. */
@Component
public class PlainTextExtractor implements TextExtractor {

    @Override
    public boolean supports(String contentType, String filename) {
        String lower = filename == null ? "" : filename.toLowerCase();
        return "text/plain".equalsIgnoreCase(contentType)
                || "text/markdown".equalsIgnoreCase(contentType)
                || lower.endsWith(".txt")
                || lower.endsWith(".md")
                || lower.endsWith(".markdown");
    }

    @Override
    public List<ExtractedPage> extract(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        return List.of(new ExtractedPage(null, content));
    }
}
