package com.auvan.api.booking.service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

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
