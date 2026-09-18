package com.studysmart.search;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion: merges ranked lists from retrievers whose raw
 * scores aren't comparable (BM25 weights vs. cosine similarities) by
 * scoring each item on its <em>rank</em> in each list, {@code 1 / (k + rank)}.
 * An item near the top of both lists beats one that is first in one list
 * and absent from the other, which is exactly the behaviour you want from
 * keyword + semantic search: agreement is strong evidence.
 */
public final class RankFusion {

    public static final int DEFAULT_K = 60;

    private RankFusion() {
    }

    @SafeVarargs
    public static List<SearchHit> fuse(int limit, List<SearchHit>... rankedLists) {
        return fuse(DEFAULT_K, limit, rankedLists);
    }

    @SafeVarargs
    public static List<SearchHit> fuse(int k, int limit, List<SearchHit>... rankedLists) {
        Map<String, Float> fused = new LinkedHashMap<>();
        for (List<SearchHit> list : rankedLists) {
            for (int rank = 0; rank < list.size(); rank++) {
                String id = list.get(rank).chunkId();
                fused.merge(id, 1f / (k + rank + 1), Float::sum);
            }
        }
        List<SearchHit> out = new ArrayList<>(fused.size());
        fused.forEach((id, score) -> out.add(new SearchHit(id, score)));
        out.sort((a, b) -> Float.compare(b.score(), a.score()));
        return out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }
}
