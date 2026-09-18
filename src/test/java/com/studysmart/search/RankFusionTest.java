package com.studysmart.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RankFusionTest {

    @Test
    void anItemRankedWellByBothRetrieversBeatsOneRankedFirstByOnlyOne() {
        List<SearchHit> keyword = List.of(hit("only-keyword"), hit("both"), hit("k3"));
        List<SearchHit> semantic = List.of(hit("only-semantic"), hit("both"), hit("s3"));

        List<SearchHit> fused = RankFusion.fuse(10, keyword, semantic);

        assertThat(fused.get(0).chunkId()).isEqualTo("both");
    }

    @Test
    void respectsTheLimitAndIgnoresAnEmptyList() {
        List<SearchHit> keyword = List.of(hit("a"), hit("b"), hit("c"), hit("d"));

        List<SearchHit> fused = RankFusion.fuse(2, keyword, List.of());

        assertThat(fused).extracting(SearchHit::chunkId).containsExactly("a", "b");
    }

    @Test
    void rawScoresDoNotLeakIntoTheFusedRanking() {
        // Wildly different score scales - only rank should matter.
        List<SearchHit> keyword = List.of(new SearchHit("x", 900f), new SearchHit("y", 800f));
        List<SearchHit> semantic = List.of(new SearchHit("y", 0.9f), new SearchHit("x", 0.8f));

        List<SearchHit> fused = RankFusion.fuse(10, keyword, semantic);

        assertThat(fused.get(0).score()).isEqualTo(fused.get(1).score());
    }

    private static SearchHit hit(String id) {
        return new SearchHit(id, 1f);
    }
}
