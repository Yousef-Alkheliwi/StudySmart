package com.studysmart.eval;

import com.studysmart.config.StudySmartProperties;
import com.studysmart.domain.Chunk;
import com.studysmart.eval.EvalCorpus.Question;
import com.studysmart.local.GroundedSource;
import com.studysmart.search.LuceneIndexManager;
import com.studysmart.search.SearchHit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measures the keyword half of retrieval on its own: for each question, is a
 * chunk that actually contains the answer among the top hits? Needs no
 * downloaded model, so it runs on every build and guards against a
 * regression in BM25 analysis.
 */
class RetrievalEval {

    private static final int TOP_K = 3;

    @TempDir
    Path tempDir;

    @Test
    void keywordSearchFindsTheAnswerChunk() throws Exception {
        StudySmartProperties properties = new StudySmartProperties();
        properties.setDataDir(tempDir.toString());
        LuceneIndexManager index = new LuceneIndexManager(properties);

        List<GroundedSource> sources = EvalCorpus.sources();
        List<Chunk> chunks = sources.stream().map(GroundedSource::chunk).toList();
        index.indexChunks("eval", chunks);
        Map<String, Chunk> byId = chunks.stream().collect(Collectors.toMap(Chunk::id, Function.identity()));

        int answerable = 0, found = 0, foundFirst = 0;
        StringBuilder report = new StringBuilder("\n=========== KEYWORD RETRIEVAL EVAL ===========\n");
        for (Question q : EvalCorpus.QUESTIONS) {
            if (!q.answerable()) {
                continue;
            }
            answerable++;
            List<SearchHit> hits = index.search("eval", q.text(), TOP_K);
            List<Chunk> hitChunks = hits.stream().map(h -> byId.get(h.chunkId())).toList();
            boolean any = hitChunks.stream().anyMatch(c -> c != null && c.content().contains(q.expected()));
            boolean first = !hitChunks.isEmpty() && hitChunks.get(0) != null
                    && hitChunks.get(0).content().contains(q.expected());
            if (any) found++;
            if (first) foundFirst++;
            report.append(String.format("%-6s %s%n", first ? "RANK1" : any ? "top" + TOP_K : "MISS", q.text()));
        }
        report.append(String.format("recall@1  : %d/%d (%.0f%%)%n", foundFirst, answerable, 100.0 * foundFirst / answerable));
        report.append(String.format("recall@%d  : %d/%d (%.0f%%)%n", TOP_K, found, answerable, 100.0 * found / answerable));
        report.append("==============================================\n");
        System.out.println(report);

        // Guards the analysis pipeline: most answers must be reachable by keywords alone.
        assertThat(found).isGreaterThanOrEqualTo((int) Math.ceil(answerable * 0.8));
    }
}
