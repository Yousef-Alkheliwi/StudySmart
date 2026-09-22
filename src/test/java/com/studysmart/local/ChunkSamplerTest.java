package com.studysmart.local;

import com.studysmart.domain.Chunk;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkSamplerTest {

    private static List<Chunk> chunks(int count) {
        List<Chunk> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(new Chunk("c" + i, "d", "p", i, 1, "text " + i, 0, 6));
        }
        return out;
    }

    @Test
    void returnsEverythingWhenItFitsInTheBudget() {
        assertThat(ChunkSampler.sample(chunks(40), 60)).hasSize(40);
        assertThat(ChunkSampler.sample(chunks(60), 60)).hasSize(60);
    }

    @Test
    void usesTheWholeBudgetEvenWhenOnlyJustOverIt() {
        // A whole-number step of 2 used to keep 31 of 61 chunks - half the
        // material thrown away for the sake of one chunk too many.
        assertThat(ChunkSampler.sample(chunks(61), 60)).hasSize(60);
        assertThat(ChunkSampler.sample(chunks(41), 40)).hasSize(40);
        assertThat(ChunkSampler.sample(chunks(90), 60)).hasSize(60);
        assertThat(ChunkSampler.sample(chunks(121), 60)).hasSize(60);
    }

    @Test
    void spansTheMaterialFromStartToEndInOrder() {
        List<Chunk> sampled = ChunkSampler.sample(chunks(500), 40);

        assertThat(sampled).hasSize(40);
        assertThat(sampled.get(0).id()).isEqualTo("c0");
        assertThat(sampled.get(sampled.size() - 1).id()).isEqualTo("c499");
        assertThat(sampled).doesNotHaveDuplicates();
        assertThat(sampled).isSortedAccordingTo((a, b) -> Integer.compare(a.ordinal(), b.ordinal()));
    }

    @Test
    void handlesDegenerateBudgets() {
        assertThat(ChunkSampler.sample(chunks(10), 1)).hasSize(1);
        assertThat(ChunkSampler.sample(chunks(10), 0)).isEmpty();
        assertThat(ChunkSampler.sample(List.of(), 5)).isEmpty();
    }
}
