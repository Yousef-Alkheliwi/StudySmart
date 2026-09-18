package com.studysmart.embedding;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HashingEmbeddingModelTest {

    private final HashingEmbeddingModel model = new HashingEmbeddingModel();

    @Test
    void vectorsAreUnitLengthAndDeterministic() {
        float[] a = model.embed("Mitochondria generate ATP through respiration.");
        float[] b = model.embed("Mitochondria generate ATP through respiration.");

        assertThat(a).hasSize(model.dimensions());
        assertThat(Vectors.dot(a, a)).isCloseTo(1f, within(1e-4f));
        assertThat(a).containsExactly(b);
    }

    @Test
    void lexicallyRelatedTextScoresHigherThanUnrelatedText() {
        float[] query = model.embed("how do mitochondria produce ATP");
        float[] related = model.embed("Mitochondria produce ATP by oxidative phosphorylation.");
        float[] unrelated = model.embed("The French Revolution began in 1789.");

        assertThat(Vectors.dot(query, related)).isGreaterThan(Vectors.dot(query, unrelated));
        assertThat(Vectors.dot(query, related)).isGreaterThan(model.relevanceFloor());
    }

    @Test
    void emptyTextProducesAZeroVectorInsteadOfNaN() {
        float[] v = model.embed("   ");
        for (float x : v) {
            assertThat(x).isEqualTo(0f);
        }
    }

    @Test
    void vectorBytesRoundTrip() {
        float[] v = model.embed("round trip");
        assertThat(Vectors.fromBytes(Vectors.toBytes(v))).containsExactly(v);
    }
}
