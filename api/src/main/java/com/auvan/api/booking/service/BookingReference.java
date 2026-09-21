package com.auvan.api.booking.service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * The customer-facing booking code, {@code AUV-YYMMDD-XXXXXXXX}.
 *
 * <p>People read this code out over the phone and type it into a chat, so the
 * alphabet drops every glyph that is mistaken for another: no {@code 0} or
 * {@code O}, no {@code 1}, {@code I} or {@code L}, and no {@code U}, which is
 * hard to tell from {@code V} when handwritten. Thirty symbols over eight
 * characters is about 39 bits, and {@code bookings_reference_unique} catches a
 * collision rather than trusting the odds.
 */
final class BookingReference {
    private static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");
    private static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private BookingReference() { }

    static String generate(OffsetDateTime now) {
        var reference = new StringBuilder("AUV-")
                .append(now.withOffsetSameInstant(ZoneOffset.UTC).format(DAY))
                .append('-');
        for (int character = 0; character < LENGTH; character++) {
            reference.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return reference.toString();
    }
}
