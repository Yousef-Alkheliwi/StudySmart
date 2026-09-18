package com.studysmart.local;

import com.studysmart.domain.Chunk;
import com.studysmart.domain.DocumentStatus;
import com.studysmart.domain.StudyDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SentenceCorpusTest {

    private static GroundedSource chunkOf(String text) {
        StudyDocument doc = new StudyDocument("d1", "p1", "notes.txt", "text/plain", text.length(), null, "/x",
                DocumentStatus.READY, null, Instant.EPOCH);
        return new GroundedSource(new Chunk("c1", "d1", "p1", 0, 1, text, 0, text.length()), doc);
    }

    @Test
    void dropsASentenceTheChunkerCutOffAtTheStart() {
        // What a chunk looks like when the boundary landed inside "Enzymes are proteins that...".
        GroundedSource source = chunkOf("that speed up biochemical reactions by lowering activation energy. "
                + "Each enzyme has an active site shaped to fit one substrate.");

        List<SourceSentence> sentences = SentenceCorpus.from(List.of(source), 50);

        assertThat(sentences).extracting(SourceSentence::text)
                .containsExactly("Each enzyme has an active site shaped to fit one substrate.");
    }

    @Test
    void dropsATrailingSentenceTheChunkerCutOffMidThought() {
        GroundedSource source = chunkOf("Glycolysis happens in the cytoplasm. "
                + "The Krebs cycle and oxidative phosphorylation happen inside the mito");

        List<SourceSentence> sentences = SentenceCorpus.from(List.of(source), 50);

        assertThat(sentences).extracting(SourceSentence::text)
                .containsExactly("Glycolysis happens in the cytoplasm.");
    }

    @Test
    void keepsBulletStyleNotesThatHaveNoFullStops() {
        // Lecture notes very often look like this; none of it may be discarded.
        GroundedSource source = chunkOf("Mitochondria produce most of the cell's ATP");

        List<SourceSentence> sentences = SentenceCorpus.from(List.of(source), 50);

        assertThat(sentences).extracting(SourceSentence::text)
                .containsExactly("Mitochondria produce most of the cell's ATP");
    }

    @Test
    void linksEachSentenceToTheOneBeforeItSoPronounsResolve() {
        GroundedSource source = chunkOf("Cellular respiration releases energy from glucose. "
                + "It occurs in three stages inside the cell.");

        List<SourceSentence> sentences = SentenceCorpus.from(List.of(source), 50);

        SourceSentence pronounLed = sentences.get(1);
        assertThat(pronounLed.needsAntecedent()).isTrue();
        assertThat(pronounLed.displayText())
                .isEqualTo("Cellular respiration releases energy from glucose. It occurs in three stages inside the cell.");
        assertThat(pronounLed.searchableText()).contains("Cellular respiration");

        SourceSentence selfContained = sentences.get(0);
        assertThat(selfContained.needsAntecedent()).isFalse();
        assertThat(selfContained.displayText()).isEqualTo(selfContained.text());
    }

    @Test
    void doesNotDuplicateASentenceSeenThroughTwoOverlappingChunks() {
        GroundedSource a = chunkOf("Enzymes lower activation energy. Glycolysis happens in the cytoplasm.");
        GroundedSource b = chunkOf("Glycolysis happens in the cytoplasm. Mitochondria make ATP for the cell.");

        List<SourceSentence> sentences = SentenceCorpus.from(List.of(a, b), 50);

        assertThat(sentences).extracting(SourceSentence::text)
                .containsExactly("Enzymes lower activation energy.",
                        "Glycolysis happens in the cytoplasm.",
                        "Mitochondria make ATP for the cell.");
    }
}
