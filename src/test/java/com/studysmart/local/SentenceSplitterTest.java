package com.studysmart.local;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SentenceSplitterTest {

    @Test
    void splitsOnTerminalPunctuationFollowedByACapital() {
        List<String> s = SentenceSplitter.split(
                "Glycolysis happens in the cytoplasm of the cell. The Krebs cycle happens in the mitochondria! Does the Krebs cycle need oxygen? Yes, the Krebs cycle is aerobic.");

        assertThat(s).containsExactly(
                "Glycolysis happens in the cytoplasm of the cell.",
                "The Krebs cycle happens in the mitochondria!",
                "Does the Krebs cycle need oxygen?",
                "Yes, the Krebs cycle is aerobic.");
    }

    @Test
    void doesNotSplitOnAbbreviationsOrInitials() {
        List<String> s = SentenceSplitter.split(
                "Enzymes, e.g. Catalase, speed up reactions in the cell. Dr. J. Smith first described the mechanism in detail.");

        assertThat(s).hasSize(2);
        assertThat(s.get(0)).startsWith("Enzymes, e.g. Catalase");
        assertThat(s.get(1)).startsWith("Dr. J. Smith");
    }

    @Test
    void joinsHardWrappedLinesAndDropsFragments() {
        List<String> s = SentenceSplitter.split("Cellular respiration breaks\ndown glucose to make ATP.\n\nEnd.");

        assertThat(s).containsExactly("Cellular respiration breaks down glucose to make ATP.");
    }

    @Test
    void keepsShortFactualSentences() {
        // Four words is a real fact; the old character-length rule threw these away.
        assertThat(SentenceSplitter.split("Mitosis has four phases. ATP stores chemical energy."))
                .containsExactly("Mitosis has four phases.", "ATP stores chemical energy.");
    }

    @Test
    void recognisesFragmentsLeftByAChunkBoundary() {
        assertThat(SentenceSplitter.startsLikeSentence("that speed up biochemical reactions.")).isFalse();
        assertThat(SentenceSplitter.startsLikeSentence("Enzymes speed up reactions.")).isTrue();
        assertThat(SentenceSplitter.startsLikeSentence("1789 was the year it began.")).isTrue();
        assertThat(SentenceSplitter.endsLikeSentence("happen inside the mito")).isFalse();
        assertThat(SentenceSplitter.endsLikeSentence("They happen inside the mitochondria.")).isTrue();
        assertThat(SentenceSplitter.endsLikeSentence("Is that right?")).isTrue();
    }

    @Test
    void blankInputYieldsNothing() {
        assertThat(SentenceSplitter.split("  \n ")).isEmpty();
        assertThat(SentenceSplitter.split(null)).isEmpty();
    }
}
