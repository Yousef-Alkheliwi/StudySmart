package com.studysmart.web;

import com.studysmart.embedding.EmbeddingModelProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Tells the UI which embedding model is powering search, so it can say so. */
@RestController
public class StatusController {

    private final EmbeddingModelProvider embeddings;

    public StatusController(EmbeddingModelProvider embeddings) {
        this.embeddings = embeddings;
    }

    @GetMapping("/api/status")
    public Map<String, Object> status() {
        return Map.of(
                "embeddings", embeddings.status(),
                "embeddingModel", embeddings.current().map(m -> m.name()).orElse("none")
        );
    }
}
