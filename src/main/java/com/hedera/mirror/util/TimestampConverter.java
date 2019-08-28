package com.hedera.mirror.util;

import org.apache.commons.lang3.StringUtils;

import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.regex.Matcher;

/**
 * Convert various time formats used by mirrornode data processing to the Instant class.
 * account balances filename timestamp, timestamp inside that file, etc.
 *
 */
public final class TimestampConverter extends Exception {
    private static final TimeZone utc = TimeZone.getTimeZone("UTC");

    /**
     * Convert to an Instant from a common regex matcher that contains named match groups: year, month, day, hour,
     * minute, second, and optionally subsecond (must be the 7th pattern match).
     * @param matcher
     * @return
     * @throws IllegalArgumentException (including NumberFormatException) if the date is not a valid date
     */
    public Instant toInstant(final Matcher matcher) throws IllegalArgumentException {
        var nanos = 0;
        if (matcher.groupCount() > 6) {
            try {
                // May come in like 007 or 111222 or 008675309
                var ss = matcher.group("subsecond");
                ss = ss.substring(0, Math.min(9, ss.length())); // Trim to 9 digits.
                ss = StringUtils.rightPad(ss, 9, "0"); // Pad-right with 0s
                nanos = Integer.parseInt(ss);
            } catch (IllegalStateException e) {
                throw new IllegalArgumentException("Failed to match timestamp regular expression 'subsecond' from " + matcher,
                        e);
            }
        }
        try {
            return toInstant(Integer.parseInt(matcher.group("year")),
                    Integer.parseInt(matcher.group("month")),
                    Integer.parseInt(matcher.group("day")),
                    Integer.parseInt(matcher.group("hour")),
                    Integer.parseInt(matcher.group("minute")),
                    Integer.parseInt(matcher.group("second")),
                    nanos);
        } catch (IllegalStateException e) {
            throw new IllegalArgumentException("Failed to match timestamp regular expression from " + matcher, e);
        }
    }

    /**
     * Create an instant at the specified Zulu time.
     * @param year
     * @param month 1-12
     * @param day 1-31
     * @param hour 0-23
     * @param minute 0-59
     * @param second 0-59
     * @param nano 0-999_999_999
     * @return
     * @throws IllegalArgumentException
     */
    public Instant toInstant(final int year, final int month, final int day, final int hour, final int minute,
                             final int second, final int nano) throws IllegalArgumentException {
        final var cal = Calendar.getInstance(utc);
        cal.clear();
        cal.setLenient(false); // Throw exception in toInstant() on "July 43, 3972 at 27:78:12"
        cal.set(year, month - 1, day, hour, minute, second);
        if ((nano < 0) || (nano > 999_999_999)) {
            throw new IllegalArgumentException("Invalid nanosecond value " + nano);
        }
        return cal.toInstant().plusNanos(nano);
    }

    public long toNanosecondLong(final Instant instant) throws ArithmeticException {
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano());
    }
}