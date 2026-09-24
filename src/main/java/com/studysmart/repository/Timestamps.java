package com.studysmart.repository;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/**
 * Writes timestamps in a form that sorts correctly as text.
 *
 * <p>Rows are ordered by their timestamp column, and SQLite compares those
 * TEXT values character by character. {@link Instant#toString()} trims
 * trailing zeros from the fraction, so it emits 0, 3, 6 or 9 digits
 * depending on the value - and a shorter fraction sorts <em>after</em> a
 * longer one that shares its prefix, because 'Z' is greater than any digit.
 * That makes "10:00:00.123Z" look later than "10:00:00.123456Z". Always
 * writing nine digits removes the ambiguity, and reading still accepts
 * anything ISO-8601.
 */
public final class Timestamps {

    /** Width of every stored timestamp: "2026-09-23T21:49:45.432000000Z". */
    public static final int STORED_LENGTH = 30;

    private static final DateTimeFormatter STORAGE = new DateTimeFormatterBuilder()
            .appendInstant(9)
            .toFormatter();

    private Timestamps() {
    }

    public static String store(Instant instant) {
        return STORAGE.format(instant);
    }

    public static Instant parse(String stored) {
        return Instant.parse(stored);
    }
}
