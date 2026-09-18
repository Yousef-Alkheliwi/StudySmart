package com.studysmart.search;

import com.studysmart.config.StudySmartProperties;
import com.studysmart.domain.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LuceneIndexManagerTest {

    private LuceneIndexManager indexManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        StudySmartProperties properties = new StudySmartProperties();
        properties.setDataDir(tempDir.toString());
        indexManager = new LuceneIndexManager(properties);
    }

    private Chunk chunk(String id, String documentId, String content) {
        return new Chunk(id, documentId, "project-1", 0, 1, content, 0, content.length());
    }

    @Test
    void ranksTheMostRelevantChunkFirst() throws Exception {
        indexManager.indexChunks("project-1", List.of(
                chunk("c1", "d1", "Mitochondria are the powerhouse of the cell and generate ATP through respiration."),
                chunk("c2", "d2", "The French Revolution began in 1789 and reshaped European politics."),
                chunk("c3", "d3", "Cellular respiration in mitochondria produces energy for the cell.")
        ));

        List<SearchHit> hits = indexManager.search("project-1", "mitochondria cell energy", 10);

        assertThat(hits).extracting(SearchHit::chunkId).containsExactlyInAnyOrder("c1", "c3");
        assertThat(hits.get(0).chunkId()).isIn("c1", "c3");
    }

    @Test
    void keepsDifferentProjectsPhysicallySeparate() throws Exception {
        indexManager.indexChunks("project-a", List.of(chunk("a1", "d1", "quantum mechanics wavefunction collapse")));
        indexManager.indexChunks("project-b", List.of(chunk("b1", "d2", "quantum mechanics wavefunction collapse")));

        List<SearchHit> hitsInA = indexManager.search("project-a", "quantum mechanics", 10);
        assertThat(hitsInA).extracting(SearchHit::chunkId).containsExactly("a1");

        indexManager.deleteProject("project-a");
        assertThat(indexManager.search("project-a", "quantum mechanics", 10)).isEmpty();
        assertThat(indexManager.search("project-b", "quantum mechanics", 10)).isNotEmpty();
    }

    @Test
    void deletingADocumentRemovesOnlyItsChunks() throws Exception {
        indexManager.indexChunks("project-1", List.of(
                chunk("c1", "keep-me", "supply and demand curves in microeconomics"),
                chunk("c2", "delete-me", "supply and demand curves in microeconomics")
        ));

        indexManager.deleteDocument("project-1", "delete-me");
        List<SearchHit> hits = indexManager.search("project-1", "supply demand curves", 10);

        assertThat(hits).extracting(SearchHit::chunkId).containsExactly("c1");
    }

    @Test
    void matchesAcrossWordEndingsAndIgnoresQuestionGrammar() throws Exception {
        indexManager.indexChunks("project-1", List.of(
                chunk("c1", "d1", "A cell produces energy by respiration."),
                chunk("c2", "d2", "Napoleon seized power in a coup in 1799.")));

        // "cells" must find "cell"; the stopwords in the question must not decide the ranking.
        List<SearchHit> hits = indexManager.search("project-1", "how do cells produce energy?", 10);

        assertThat(hits).extracting(SearchHit::chunkId).containsExactly("c1");
    }

    @Test
    void reportsAnIndexWrittenByAnOlderAnalyzerAsStale() throws Exception {
        indexManager.indexChunks("project-1", List.of(chunk("c1", "d1", "some content")));
        assertThat(indexManager.isStale("project-1")).isFalse();

        // Simulate an index built before the analyzer changed.
        java.nio.file.Files.writeString(tempDir.resolve("lucene").resolve("project-1").resolve(".analyzer-version"), "older");
        assertThat(indexManager.isStale("project-1")).isTrue();

        assertThat(indexManager.isStale("project-never-indexed")).isFalse();
    }

    @Test
    void emptyQueryReturnsNoHits() throws Exception {
        indexManager.indexChunks("project-1", List.of(chunk("c1", "d1", "some content")));
        assertThat(indexManager.search("project-1", "   ", 10)).isEmpty();
    }

    @Test
    void searchingAProjectWithNoIndexReturnsEmpty() throws Exception {
        assertThat(indexManager.search("never-indexed", "anything", 10)).isEmpty();
    }
}
