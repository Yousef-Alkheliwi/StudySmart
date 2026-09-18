package com.studysmart.local;

import com.studysmart.domain.Chunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Downsamples a large chunk list to a token/cost budget by taking an even
 * stride across the whole list, rather than truncating from the start - so a
 * quiz or summary built from many documents still draws material from all of
 * them instead of only the first one alphabetically.
 */
public final class ChunkSampler {

    private ChunkSampler() {
    }

    public static List<Chunk> sample(List<Chunk> chunks, int max) {
        if (chunks.size() <= max) {
            return chunks;
        }
        int step = (int) Math.ceil((double) chunks.size() / max);
        List<Chunk> sampled = new ArrayList<>();
        for (int i = 0; i < chunks.size() && sampled.size() < max; i += step) {
            sampled.add(chunks.get(i));
        }
        return sampled;
    }
}
