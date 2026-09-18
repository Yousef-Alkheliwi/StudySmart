package com.studysmart.search;

import com.studysmart.embedding.HashingEmbeddingModel;
import com.studysmart.repository.ChunkEmbeddingRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VectorIndexTest {

    private final HashingEmbeddingModel model = new HashingEmbeddingModel();

    /** In-memory stand-in for the SQLite-backed repository. */
    private class FakeRepository extends ChunkEmbeddingRepository {
        final List<StoredVector> stored = new ArrayList<>();
        int loads = 0;

        FakeRepository() {
            super(null);
        }

        void add(String chunkId, String text) {
            stored.add(new StoredVector(chunkId, model.embed(text)));
        }

        @Override
        public List<StoredVector> findByProject(String projectId, String modelName) {
            loads++;
            return List.copyOf(stored);
        }
    }

    @Test
    void returnsTheClosestChunksFirst() {
        FakeRepository repo = new FakeRepository();
        repo.add("bio", "Mitochondria produce ATP through oxidative phosphorylation.");
        repo.add("history", "The French Revolution began in 1789 in Paris.");
        repo.add("chem", "Covalent bonds share electron pairs between atoms.");
        VectorIndex index = new VectorIndex(repo);

        List<SearchHit> hits = index.search("p1", model, "how is ATP produced in mitochondria", 2);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).chunkId()).isEqualTo("bio");
        assertThat(hits.get(0).score()).isGreaterThan(hits.get(1).score());
    }

    @Test
    void cachesVectorsUntilInvalidated() {
        FakeRepository repo = new FakeRepository();
        repo.add("a", "some text about photosynthesis");
        VectorIndex index = new VectorIndex(repo);

        index.search("p1", model, "photosynthesis", 5);
        index.search("p1", model, "photosynthesis again", 5);
        assertThat(repo.loads).isEqualTo(1);

        index.invalidate("p1");
        index.search("p1", model, "photosynthesis", 5);
        assertThat(repo.loads).isEqualTo(2);
    }

    @Test
    void emptyProjectYieldsNoHits() {
        VectorIndex index = new VectorIndex(new FakeRepository());
        assertThat(index.search("empty", model, "anything", 5)).isEmpty();
    }
}
