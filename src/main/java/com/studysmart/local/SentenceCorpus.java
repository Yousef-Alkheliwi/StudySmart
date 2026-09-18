package com.studysmart.local;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Builds the de-duplicated sentence list the on-device engine works over. */
public final class SentenceCorpus {

    private SentenceCorpus() {
    }

    public static List<SourceSentence> from(List<GroundedSource> sources, int maxSentences) {
        List<SourceSentence> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int position = 0;
        for (GroundedSource source : sources) {
            for (String sentence : SentenceSplitter.split(source.chunk().content())) {
                SourceSentence candidate = new SourceSentence(sentence, source, position++);
                // Consecutive chunks overlap on purpose, so the same sentence
                // often arrives twice; keep the first sighting only.
                if (seen.add(candidate.identity())) {
                    out.add(candidate);
                    if (out.size() >= maxSentences) {
                        return out;
                    }
                }
            }
        }
        return out;
    }
}
