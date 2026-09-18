package com.studysmart.ingest;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkerTest {

    private static final Pattern WORD = Pattern.compile("\\S+");

    private int wordCount(String text) {
        int count = 0;
        Matcher m = WORD.matcher(text);
        while (m.find()) {
            count++;
        }
        return count;
    }

    @Test
    void producesASingleChunkWhenTextIsShorterThanTarget() {
        Chunker chunker = new Chunker(180, 30);
        String text = "The mitochondria is the powerhouse of the cell.";
        List<ChunkCandidate> chunks = chunker.chunk(List.of(new ExtractedPage(1, text)));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).content()).isEqualTo(text);
        assertThat(chunks.get(0).page()).isEqualTo(1);
    }

    @Test
    void splitsLongTextIntoMultipleOverlappingChunks() {
        Chunker chunker = new Chunker(50, 10);
        String longText = "word ".repeat(500).trim();
        List<ChunkCandidate> chunks = chunker.chunk(List.of(new ExtractedPage(null, longText)));

        assertThat(chunks.size()).isGreaterThan(5);
        for (ChunkCandidate c : chunks) {
            assertThat(wordCount(c.content())).isLessThanOrEqualTo(60); // target + boundary slack
        }
    }

    @Test
    void consecutiveChunksShareOverlappingWords() {
        Chunker chunker = new Chunker(40, 10);
        // No blank lines, so no paragraph boundary snapping interferes with the overlap check.
        String text = String.join(" ",
                java.util.stream.IntStream.range(0, 200).mapToObj(i -> "w" + i).toList());
        List<ChunkCandidate> chunks = chunker.chunk(List.of(new ExtractedPage(null, text)));

        assertThat(chunks.size()).isGreaterThan(1);
        // With a 10-word overlap, the last 10 words of chunk 1 must open chunk 2 verbatim.
        String firstTail = lastNWords(chunks.get(0).content(), 10);
        assertThat(chunks.get(1).content()).startsWith(firstTail);
    }

    @Test
    void charOffsetsRoundTripIntoTheOriginalText() {
        Chunker chunker = new Chunker(30, 5);
        String text = "Alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma tau upsilon phi chi psi omega";
        List<ChunkCandidate> chunks = chunker.chunk(List.of(new ExtractedPage(2, text)));

        for (ChunkCandidate c : chunks) {
            assertThat(text.substring(c.charStart(), c.charEnd())).isEqualTo(c.content());
        }
    }

    @Test
    void blankPageProducesNoChunks() {
        Chunker chunker = new Chunker(180, 30);
        List<ChunkCandidate> chunks = chunker.chunk(List.of(new ExtractedPage(1, "   \n\n  ")));
        assertThat(chunks).isEmpty();
    }

    @Test
    void rejectsInvalidOverlapConfiguration() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new Chunker(50, 50));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new Chunker(0, 0));
    }

    private String lastNWords(String text, int n) {
        String[] words = text.trim().split("\\s+");
        int from = Math.max(0, words.length - n);
        return String.join(" ", java.util.Arrays.copyOfRange(words, from, words.length));
    }
}
