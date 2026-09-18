package com.studysmart.local;

import com.studysmart.domain.Chunk;
import com.studysmart.domain.StudyDocument;

/** A retrieved chunk paired with the document it came from, so anything built from it can be cited back to a file and page. */
public record GroundedSource(Chunk chunk, StudyDocument document) {

    public String title() {
        return chunk.page() != null
                ? document.filename() + " — p. " + chunk.page()
                : document.filename();
    }
}
