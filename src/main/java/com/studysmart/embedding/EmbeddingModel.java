package com.studysmart.embedding;

import java.util.List;

/**
 * Turns text into a fixed-size vector where semantically similar text lands
 * close together. Every vector returned is L2-normalized, so cosine
 * similarity is just a dot product ({@link Vectors#dot}).
 */
public interface EmbeddingModel {

    /** Stable identifier stored alongside each vector, so a model change invalidates old vectors rather than silently mixing spaces. */
    String name();

    int dimensions();

    /**
     * Lowest similarity at which a sentence should be treated as actually
     * relevant to a query. Model-specific: a learned model separates
     * unrelated text far more cleanly than a lexical hash does.
     */
    float relevanceFloor();

    float[] embed(String text);

    List<float[]> embedAll(List<String> texts);
}
