package com.studysmart.ingest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface TextExtractor {

    boolean supports(String contentType, String filename);

    /** Returns one entry per page (or a single page-less entry for flat text formats). */
    List<ExtractedPage> extract(Path file) throws IOException;
}
