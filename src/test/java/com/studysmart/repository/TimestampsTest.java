package com.studysmart.repository;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class TimestampsTest {

    @Test
    void storedFormIsAlwaysTheSameWidth() {
        List<Instant> awkward = List.of(
                Instant.parse("2026-09-23T10:00:00Z"),            // no fraction at all
                Instant.parse("2026-09-23T10:00:00.123Z"),        // milliseconds
                Instant.parse("2026-09-23T10:00:00.123456Z"),     // microseconds
                Instant.parse("2026-09-23T10:00:00.123456789Z")); // nanoseconds

        assertThat(awkward).allSatisfy(instant ->
                assertThat(Timestamps.store(instant)).hasSize(Timestamps.STORED_LENGTH));
    }

    @Test
    void storedFormSortsInTheSameOrderAsTheInstants() {
        // Instant.toString() drops trailing zeros, so ".123Z" used to sort
        // after ".123456Z" - 'Z' is greater than any digit.
        Instant earlier = Instant.parse("2026-09-23T10:00:00.123Z");
        Instant later = Instant.parse("2026-09-23T10:00:00.123456Z");

        assertThat(earlier).isBefore(later);
        assertThat(earlier.toString().compareTo(later.toString()))
                .as("the old format got this backwards")
                .isPositive();
        assertThat(Timestamps.store(earlier).compareTo(Timestamps.store(later)))
                .as("the stored format must agree with the clock")
                .isNegative();
    }

    @Test
    void textOrderMatchesTimeOrderAcrossManyRandomInstants() {
        Random random = new Random(42);
        List<Instant> instants = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            // Mix whole seconds, milli, micro and nano precision in one batch.
            long nanos = switch (i % 4) {
                case 0 -> 0;
                case 1 -> random.nextInt(1000) * 1_000_000L;
                case 2 -> random.nextInt(1_000_000) * 1_000L;
                default -> random.nextInt(1_000_000_000);
            };
            instants.add(Instant.parse("2026-09-23T10:00:00Z").plusNanos(nanos));
        }

        List<Instant> byClock = instants.stream().sorted().toList();
        List<Instant> byStoredText = instants.stream()
                .sorted((a, b) -> Timestamps.store(a).compareTo(Timestamps.store(b)))
                .toList();

        assertThat(byStoredText).isEqualTo(byClock);
    }

    @Test
    void roundTripsThroughStorage() {
        Instant instant = Instant.parse("2026-09-23T21:49:45.432000000Z");
        assertThat(Timestamps.parse(Timestamps.store(instant))).isEqualTo(instant);
        // Reading still accepts whatever earlier versions wrote.
        assertThat(Timestamps.parse("2026-09-23T10:00:00Z")).isEqualTo(Instant.parse("2026-09-23T10:00:00Z"));
    }
}
