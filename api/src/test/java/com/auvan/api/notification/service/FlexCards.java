package com.auvan.api.notification.service;

import com.auvan.api.notification.client.FlexMessage;
import com.auvan.api.notification.config.LineMessagingProperties;
import com.auvan.api.notification.dto.BookingNotification;
import com.auvan.api.notification.dto.WaitlistNotification;
import com.auvan.api.outbox.entity.OutboxEventType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class FlexCards {
    public static final String LIFF_URL = "https://liff.line.me/2000000000-abcdefgh";
    public static final OffsetDateTime FRIDAY_22_14_BANGKOK = OffsetDateTime.parse("2026-10-02T15:14:00Z");
    public static final OffsetDateTime FRIDAY_16_00_BANGKOK = OffsetDateTime.parse("2026-10-02T09:00:00Z");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private FlexCards() { }

    public static FlexMessageFactory factory(String liffUrl) {
        return new FlexMessageFactory(new LineMessagingProperties("", "https://api.line.me", true, liffUrl));
    }

    public static boolean isWaitlist(OutboxEventType type) {
        return type == OutboxEventType.WAITLIST_PROMOTED || type == OutboxEventType.WAITLIST_PROMOTION_EXPIRED;
    }

    public static BookingNotification booking(String detail) {
        return new BookingNotification("AUV-261002-ABCD", detail, "AU Suvarnabhumi", "Siam Paragon",
                FRIDAY_22_14_BANGKOK, List.of("A1", "A2"), new BigDecimal("70"), FRIDAY_16_00_BANGKOK);
    }

    public static BookingNotification oldBooking(String detail) {
        return new BookingNotification("AUV-250101-OLD", detail, null, null, null, null, null, null);
    }

    public static WaitlistNotification waitlist() {
        return new WaitlistNotification("AU Suvarnabhumi to Siam Paragon, departing 2 Oct 2026 at 15:14.",
                "Take the seats by 2 Oct 2026 at 09:00.", "AU Suvarnabhumi", "Siam Paragon",
                FRIDAY_22_14_BANGKOK, List.of("B3"), new BigDecimal("35.00"), FRIDAY_16_00_BANGKOK);
    }

    public static WaitlistNotification oldWaitlist() {
        return new WaitlistNotification("AU to Asok, departing 2 Oct 2026 at 15:14.",
                "Take the seats by 2 Oct 2026 at 09:00.", null, null, null, null, null, null);
    }

    public static Object payloadFor(OutboxEventType type) {
        return isWaitlist(type) ? waitlist() : booking("Booked seats A1, A2.");
    }

    public static FlexMessage render(FlexMessageFactory cards, OutboxEventType type, Object payload) {
        return isWaitlist(type)
                ? cards.waitlist(type, (WaitlistNotification) payload)
                : cards.booking(type, (BookingNotification) payload);
    }

    public static JsonNode tree(FlexMessage message) {
        return JSON.valueToTree(message);
    }

    public static String json(FlexMessage message) {
        return JSON.writeValueAsString(message);
    }

    public static List<String> texts(FlexMessage message) {
        return tree(message).path("contents").findValuesAsString("text");
    }

    public static List<JsonNode> buttons(FlexMessage message) {
        return tree(message).findParents("action");
    }
}
