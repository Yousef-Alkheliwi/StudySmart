package com.studysmart.embedding;

import java.util.Set;

/** Function words that carry no topical signal; excluded from lexical features so they can't make unrelated texts look similar. */
public final class Stopwords {

    private Stopwords() {
    }

    public static final Set<String> ENGLISH = Set.of(
            "a", "an", "the", "and", "or", "but", "if", "then", "than", "so", "as", "of", "at", "by", "for", "with",
            "about", "into", "onto", "from", "to", "in", "on", "over", "under", "between", "through", "during",
            "before", "after", "above", "below", "up", "down", "out", "off", "again", "further", "once", "here",
            "there", "when", "where", "why", "how", "what", "which", "who", "whom", "whose", "this", "that", "these",
            "those", "is", "are", "was", "were", "be", "been", "being", "have", "has", "had", "having", "do", "does",
            "did", "doing", "will", "would", "shall", "should", "can", "could", "may", "might", "must", "not", "no",
            "nor", "only", "own", "same", "such", "too", "very", "just", "also", "it", "its", "they", "them", "their",
            "we", "our", "you", "your", "he", "she", "his", "her", "i", "me", "my", "all", "any", "both", "each",
            "few", "more", "most", "other", "some", "because", "while", "however", "therefore", "thus", "hence",
            "although", "though", "since", "until", "whether", "either", "neither", "one", "two", "three", "many",
            "much", "well", "often", "usually", "called", "known", "example", "explain", "describe", "define",
            "mean", "means", "tell", "give", "list", "used", "using", "use", "like", "within", "without");

    public static boolean isStopword(String token) {
        return ENGLISH.contains(token);
    }
}
