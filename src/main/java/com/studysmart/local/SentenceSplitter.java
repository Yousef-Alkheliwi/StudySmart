package com.studysmart.local;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits prose into sentences. Terminal punctuation followed by whitespace
 * and an uppercase letter, digit or opening quote is a boundary, unless the
 * word before it is a common abbreviation ("e.g.", "Dr.", "Fig.") or a
 * single initial ("J. Smith"). Newlines inside a chunk are treated as
 * spaces first, since PDF text extraction hard-wraps lines.
 */
public final class SentenceSplitter {

    private static final Pattern BOUNDARY = Pattern.compile("(?<=[.!?])\\s+(?=[\"'\\(\\[A-Z0-9])");
    private static final Set<String> ABBREVIATIONS = Set.of(
            "e.g.", "i.e.", "etc.", "vs.", "cf.", "dr.", "mr.", "mrs.", "ms.", "prof.", "fig.", "eq.", "no.",
            "st.", "jr.", "sr.", "inc.", "ltd.", "approx.", "dept.", "univ.", "ch.", "sec.", "vol.", "pp.", "p.");
    /** Four words is enough for a real fact ("Glycolysis needs no oxygen."); below that it is a heading or a stray label. */
    private static final int MIN_WORDS = 4;
    private static final Pattern OPENS_SENTENCE = Pattern.compile("^[\\p{Lu}\\p{N}\"'\u201c(\\[]");
    private static final Pattern CLOSES_SENTENCE = Pattern.compile("[.!?][\")\\]\u201d']?$");

    private SentenceSplitter() {
    }

    public static List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String flattened = text.replaceAll("\\s*\\n\\s*", " ").replaceAll("\\s{2,}", " ").trim();

        List<String> pieces = new ArrayList<>();
        Matcher m = BOUNDARY.matcher(flattened);
        int start = 0;
        while (m.find()) {
            String candidate = flattened.substring(start, m.start());
            if (endsWithAbbreviation(candidate)) {
                continue;
            }
            pieces.add(candidate);
            start = m.end();
        }
        pieces.add(flattened.substring(start));

        List<String> sentences = new ArrayList<>();
        for (String piece : pieces) {
            String s = piece.trim();
            if (wordCount(s) >= MIN_WORDS) {
                sentences.add(s);
            }
        }
        return sentences;
    }

    /**
     * True when the piece begins the way a sentence begins - a capital,
     * digit or opening quote. A chunk cut mid-sentence leaves its first
     * piece starting lowercase ("that speed up biochemical reactions..."),
     * which is the reliable tell for a leading fragment.
     */
    public static boolean startsLikeSentence(String sentence) {
        String s = sentence == null ? "" : sentence.trim();
        return !s.isEmpty() && OPENS_SENTENCE.matcher(s).find();
    }

    /**
     * True when the piece closes on terminal punctuation. Only meaningful
     * for the last piece of a chunk, and only when the chunk had more than
     * one: plenty of real lecture notes are bullet lines with no full stop,
     * and those must not be mistaken for truncation.
     */
    public static boolean endsLikeSentence(String sentence) {
        String s = sentence == null ? "" : sentence.trim();
        return !s.isEmpty() && CLOSES_SENTENCE.matcher(s).find();
    }

    private static int wordCount(String s) {
        return s.isBlank() ? 0 : s.trim().split("\\s+").length;
    }

    private static boolean endsWithAbbreviation(String candidate) {
        int lastSpace = candidate.lastIndexOf(' ');
        String lastWord = (lastSpace == -1 ? candidate : candidate.substring(lastSpace + 1)).toLowerCase();
        if (ABBREVIATIONS.contains(lastWord)) {
            return true;
        }
        // Single-letter initial such as "J." in "J. Smith".
        return lastWord.length() == 2 && Character.isLetter(lastWord.charAt(0)) && lastWord.charAt(1) == '.';
    }
}
