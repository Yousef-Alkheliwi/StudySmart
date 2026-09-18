package com.studysmart.local;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the de-duplicated sentence list the on-device engine works over.
 *
 * <p>Two things happen here that decide answer quality. Chunks are cut on a
 * word budget, so a chunk's first sentence often starts mid-thought
 * ("...that speed up biochemical reactions.") and its last one can stop
 * mid-thought; those fragments are dropped, because the overlap between
 * neighbouring chunks means the whole sentence exists elsewhere. And every
 * surviving sentence keeps a link to the one before it, so a sentence
 * opening with "It" or "They" can still be understood and found.
 */
public final class SentenceCorpus {

    private SentenceCorpus() {
    }

    public static List<SourceSentence> from(List<GroundedSource> sources, int maxSentences) {
        List<SourceSentence> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int position = 0;

        for (GroundedSource source : sources) {
            List<String> pieces = SentenceSplitter.split(source.chunk().content());
            String previous = null;
            for (int i = 0; i < pieces.size(); i++) {
                String sentence = pieces.get(i);
                if (isChunkBoundaryFragment(sentence, i, pieces.size())) {
                    // Skip it, and don't offer half a thought as the next
                    // sentence's antecedent either.
                    previous = null;
                    continue;
                }
                SourceSentence candidate = new SourceSentence(sentence, previous, source, position++);
                previous = sentence;
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

    private static boolean isChunkBoundaryFragment(String sentence, int index, int pieceCount) {
        if (!SentenceSplitter.startsLikeSentence(sentence)) {
            return true;
        }
        boolean isTrailingPiece = index == pieceCount - 1 && pieceCount > 1;
        return isTrailingPiece && !SentenceSplitter.endsLikeSentence(sentence);
    }
}
