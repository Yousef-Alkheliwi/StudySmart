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
        if (max <= 0) {
            return List.of();
        }
        if (chunks.size() <= max) {
            return chunks;
        }
        if (max == 1) {
            return List.of(chunks.get(0));
        }
        // Spread the picks across the whole list rather than stepping by a
        // whole number: a whole-number step of 2 over 61 chunks with room for
        // 60 threw away half the material for the sake of one chunk too many.
        List<Chunk> sampled = new ArrayList<>(max);
        int lastIndex = -1;
        for (int i = 0; i < max; i++) {
            int index = (int) Math.round((double) i * (chunks.size() - 1) / (max - 1));
            if (index > lastIndex) {
                sampled.add(chunks.get(index));
                lastIndex = index;
            }
        }
        return sampled;
    }
}
