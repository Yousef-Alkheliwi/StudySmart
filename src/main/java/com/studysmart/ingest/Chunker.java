package com.studysmart.ingest;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits page text into overlapping, retrieval-sized chunks.
 *
 * <p>This is a word-count sliding window ({@code targetWords} per chunk,
 * {@code overlapWords} shared between consecutive chunks so an idea split
 * across a boundary is never invisible to search) that snaps its cut points
 * to the nearest blank-line paragraph break when one exists nearby, instead
 * of always slicing mid-sentence. Falls back to a hard word cut when no
 * paragraph break is close enough, so lecture-note-style prose (no blank
 * lines at all) still chunks cleanly.
 */
public class Chunker {

    private static final Pattern WORD = Pattern.compile("\\S+");
    private static final Pattern BLANK_LINE = Pattern.compile("\\n\\s*\\n");

    private final int targetWords;
    private final int overlapWords;
    private final int boundarySearchRadius;

    public Chunker(int targetWords, int overlapWords) {
        if (targetWords <= 0) {
            throw new IllegalArgumentException("targetWords must be positive");
        }
        if (overlapWords < 0 || overlapWords >= targetWords) {
            throw new IllegalArgumentException("overlapWords must be >= 0 and less than targetWords");
        }
        this.targetWords = targetWords;
        this.overlapWords = overlapWords;
        this.boundarySearchRadius = Math.max(5, targetWords / 5);
    }

    public List<ChunkCandidate> chunk(List<ExtractedPage> pages) {
        List<ChunkCandidate> result = new ArrayList<>();
        for (ExtractedPage page : pages) {
            result.addAll(chunkPage(page));
        }
        return result;
    }

    List<ChunkCandidate> chunkPage(ExtractedPage page) {
        String text = page.text();
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<int[]> words = tokenize(text);
        if (words.isEmpty()) {
            return List.of();
        }

        boolean[] boundaryBeforeWord = paragraphBoundaries(text, words);
        List<ChunkCandidate> chunks = new ArrayList<>();
        int n = words.size();
        int windowStart = 0;

        while (windowStart < n) {
            int naiveEnd = Math.min(n, windowStart + targetWords);
            int windowEnd = naiveEnd == n ? n : snapToBoundary(boundaryBeforeWord, naiveEnd, windowStart);

            int charStart = words.get(windowStart)[0];
            int charEnd = words.get(windowEnd - 1)[1];
            chunks.add(new ChunkCandidate(page.pageNumber(), text.substring(charStart, charEnd), charStart, charEnd));

            if (windowEnd >= n) {
                break;
            }
            windowStart = Math.max(windowEnd - overlapWords, windowStart + 1);
        }
        return chunks;
    }

    private int snapToBoundary(boolean[] boundaryBeforeWord, int naiveEnd, int windowStart) {
        int lo = Math.max(windowStart + 1, naiveEnd - boundarySearchRadius);
        int hi = Math.min(boundaryBeforeWord.length - 1, naiveEnd + boundarySearchRadius);
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = lo; i <= hi; i++) {
            if (boundaryBeforeWord[i]) {
                int distance = Math.abs(i - naiveEnd);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = i;
                }
            }
        }
        return best == -1 ? naiveEnd : best;
    }

    private List<int[]> tokenize(String text) {
        List<int[]> words = new ArrayList<>();
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            words.add(new int[]{matcher.start(), matcher.end()});
        }
        return words;
    }

    /** {@code result[i]} is true when a blank-line paragraph break falls immediately before word {@code i}. */
    private boolean[] paragraphBoundaries(String text, List<int[]> words) {
        boolean[] boundary = new boolean[words.size() + 1];
        Matcher matcher = BLANK_LINE.matcher(text);
        int wordPointer = 0;
        while (matcher.find()) {
            int breakEnd = matcher.end();
            while (wordPointer < words.size() && words.get(wordPointer)[0] < breakEnd) {
                wordPointer++;
            }
            boundary[wordPointer] = true;
        }
        return boundary;
    }
}
