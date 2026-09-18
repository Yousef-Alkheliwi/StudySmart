package com.studysmart.local;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits extracted document text into sentences.
 *
 * <p>Two very different shapes of text arrive here. Prose from a paper or a
 * textbook is hard-wrapped by PDF extraction, so its line breaks are
 * meaningless and must be stitched back together. A slide deck or a page of
 * lecture notes is the opposite: its line breaks are the only structure it
 * has, since titles and bullets rarely end in a full stop. Flattening every
 * newline - as this used to - turns a whole slide into one run-on
 * "sentence", which then shows up verbatim as a quiz question or an answer.
 *
 * <p>So lines are kept as units unless the next line clearly continues the
 * previous one, bullet markers and slide numbers are stripped, and ordinary
 * sentence punctuation is then applied within each line.
 */
public final class SentenceSplitter {

    private static final Pattern BOUNDARY = Pattern.compile("(?<=[.!?])\\s+(?=[\"'\\(\\[A-Z0-9])");
    private static final Set<String> ABBREVIATIONS = Set.of(
            "e.g.", "i.e.", "etc.", "vs.", "cf.", "dr.", "mr.", "mrs.", "ms.", "prof.", "fig.", "eq.", "no.",
            "st.", "jr.", "sr.", "inc.", "ltd.", "approx.", "dept.", "univ.", "ch.", "sec.", "vol.", "pp.", "p.");
    /** Four words is enough for a real fact ("Glycolysis needs no oxygen."); below that it is a heading or a stray label. */
    private static final int MIN_WORDS = 4;
    private static final Pattern OPENS_SENTENCE = Pattern.compile("^[\\p{Lu}\\p{N}\"'“(\\[]");
    private static final Pattern CLOSES_SENTENCE = Pattern.compile("[.!?][\")\\]”']?$");
    /** Bullet glyphs and dashes a slide or a note uses to open a line. */
    private static final Pattern BULLET = Pattern.compile("^\\s*[•▪●◦‣·\\-–—*o]\\s+");
    /** A line that is only a slide or page number, or only punctuation. */
    private static final Pattern PAGE_FURNITURE = Pattern.compile("^\\s*(\\d{1,4}|[\\p{Punct}\\s]+)\\s*$");

    private SentenceSplitter() {
    }

    public static List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> sentences = new ArrayList<>();
        for (String line : meaningfulLines(text)) {
            for (String piece : splitOnPunctuation(line)) {
                String s = piece.trim();
                if (wordCount(s) >= MIN_WORDS) {
                    sentences.add(s);
                }
            }
        }
        return sentences;
    }

    /**
     * Turns raw extracted text into the lines that carry meaning: bullets
     * and slide numbers removed, and wrapped lines rejoined.
     */
    private static List<String> meaningfulLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\\R")) {
            String line = BULLET.matcher(raw.trim()).replaceFirst("").trim();
            if (line.isEmpty() || PAGE_FURNITURE.matcher(line).matches()) {
                continue;
            }
            if (!lines.isEmpty() && continuesPreviousLine(lines.get(lines.size() - 1), line, raw)) {
                lines.set(lines.size() - 1, lines.get(lines.size() - 1) + " " + line);
            } else {
                lines.add(line);
            }
        }
        return lines;
    }

    /**
     * True when a line is the tail of the one above rather than something
     * new. PDF extraction wraps prose mid-thought, and the giveaway is that
     * the previous line stopped without punctuation and this one starts
     * mid-sentence - lowercase, or closing what the previous line opened.
     * A new bullet or a new capitalised line is never a continuation.
     */
    private static boolean continuesPreviousLine(String previous, String line, String rawLine) {
        if (BULLET.matcher(rawLine.trim()).find()) {
            return false;
        }
        char lastChar = previous.charAt(previous.length() - 1);
        if (".!?:;".indexOf(lastChar) >= 0) {
            return false;
        }
        char first = line.charAt(0);
        return Character.isLowerCase(first) || first == ')' || first == ']';
    }

    private static List<String> splitOnPunctuation(String line) {
        List<String> pieces = new ArrayList<>();
        Matcher m = BOUNDARY.matcher(line);
        int start = 0;
        while (m.find()) {
            String candidate = line.substring(start, m.start());
            if (endsWithAbbreviation(candidate)) {
                continue;
            }
            pieces.add(candidate);
            start = m.end();
        }
        pieces.add(line.substring(start));
        return pieces;
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
     * for the last piece of a chunk of prose: slides and bullet notes end
     * lines without a full stop all the time, and those must not be
     * mistaken for truncation.
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
