package com.auvan.api.notification.service;

import com.auvan.api.notification.client.FlexMessage;
import com.auvan.api.notification.dto.BookingNotification;
import com.auvan.api.outbox.entity.OutboxEventType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static com.auvan.api.notification.service.FlexCards.LIFF_URL;
import static com.auvan.api.notification.service.FlexCards.booking;
import static com.auvan.api.notification.service.FlexCards.buttons;
import static com.auvan.api.notification.service.FlexCards.factory;
import static com.auvan.api.notification.service.FlexCards.oldBooking;
import static com.auvan.api.notification.service.FlexCards.oldWaitlist;
import static com.auvan.api.notification.service.FlexCards.texts;
import static com.auvan.api.notification.service.FlexCards.tree;
import static com.auvan.api.notification.service.FlexCards.waitlist;
import static org.assertj.core.api.Assertions.assertThat;

class FlexMessageFactoryTests {
    private final FlexMessageFactory cards = factory(LIFF_URL);

    @Test
    void aHeldBookingIsABoardingPassCarryingEveryFactAndTheSlipButton() {
        FlexMessage message = cards.booking(OutboxEventType.BOOKING_CREATED, booking("Booked seats A1, A2."));

        assertThat(message.type()).isEqualTo("flex");
        assertThat(message.altText()).isEqualTo("AU-Van booking AUV-261002-ABCD\n"
                + "Your seats are held. Send your payment proof to keep them.\nBooked seats A1, A2.");
        assertThat(texts(message)).containsSubsequence("AU·VAN · BOOKING", "Seats held", "Action needed",
                "AU Suvarnabhumi", "Siam Paragon", "Departs", "Fri 2 Oct · 22:14", "Seats", "A1, A2",
                "Fare", "70.00 THB", "Pay by", "Fri 2 Oct · 16:00", "Booking ref AUV-261002-ABCD");
        assertThat(tree(message).path("contents").path("header").path("backgroundColor").asString())
                .isEqualTo("#1b2566");
        assertThat(buttons(message)).singleElement().satisfies(button -> {
            assertThat(button.path("style").asString()).isEqualTo("primary");
            assertThat(button.path("color").asString()).isEqualTo("#3041a1");
            assertThat(button.path("action").path("label").asString()).isEqualTo("Upload payment slip");
            assertThat(button.path("action").path("uri").asString()).isEqualTo(LIFF_URL);
        });
    }

    @Test
    void theStatusWordAndItsColourMatchTheEvent() {
        assertThat(pillOf(cards.booking(OutboxEventType.PAYMENT_APPROVED, booking("Payment approved."))))
                .isEqualTo("Confirmed #2e7a57");
        assertThat(pillOf(cards.booking(OutboxEventType.BOOKING_CANCELLED, booking("Cancelled."))))
                .isEqualTo("Cancelled #b23b2e");
        assertThat(pillOf(cards.booking(OutboxEventType.DEPARTURE_REMINDER_1H, booking("AU to Asok."))))
                .isEqualTo("Reminder #3041a1");
        assertThat(pillOf(cards.waitlist(OutboxEventType.WAITLIST_PROMOTED, waitlist())))
                .isEqualTo("Held for you #d99a2b");
    }

    @Test
    void onlyAnEventWithANextStepCarriesAButtonAndEachOpensTheLiffApp() {
        for (OutboxEventType type : OutboxEventType.values()) {
            FlexMessage message = FlexCards.render(cards, type, FlexCards.payloadFor(type));
            String expected = switch (type) {
                case BOOKING_CREATED, PAYMENT_REJECTED -> "Upload payment slip";
                case PAYMENT_APPROVED, DEPARTURE_REMINDER_24H, DEPARTURE_REMINDER_1H, TRIP_RESCHEDULED ->
                        "View booking";
                case WAITLIST_PROMOTED -> "Take the seat";
                case BOOKING_CANCELLED, BOOKING_EXPIRED, PAYMENT_PROOF_SUBMITTED, WAITLIST_PROMOTION_EXPIRED,
                     TRIP_CANCELLED -> null;
            };

            assertThat(buttons(message).stream().map(button -> button.path("action").path("label").asString()))
                    .as(type.name())
                    .containsExactlyElementsOf(expected == null ? List.of() : List.of(expected));
            assertThat(tree(message).path("contents").has("footer")).as(type.name()).isEqualTo(expected != null);
        }
    }

    @Test
    void aBlankLiffUrlLeavesEveryCardWithoutAFooterOrAButton() {
        for (String blank : new String[] {"", "   ", null}) {
            FlexMessageFactory unconfigured = factory(blank);
            for (OutboxEventType type : OutboxEventType.values()) {
                FlexMessage message = FlexCards.render(unconfigured, type, FlexCards.payloadFor(type));

                assertThat(tree(message).path("contents").has("footer")).as(type.name()).isFalse();
                assertThat(buttons(message)).as(type.name()).isEmpty();
            }
        }
    }

    @Test
    void aRejectionShowsTheAdminsReasonAndAsksForANewSlip() {
        FlexMessage message = cards.booking(OutboxEventType.PAYMENT_REJECTED, booking("The slip is unreadable."));

        assertThat(texts(message)).containsSubsequence("Payment slip not accepted", "Not accepted",
                "Reason", "The slip is unreadable.", "AU Suvarnabhumi", "Pay by", "Fri 2 Oct · 16:00");
        assertThat(buttons(message)).extracting(button -> button.path("action").path("label").asString())
                .containsExactly("Upload payment slip");
    }

    @Test
    void aRejectionWrittenBeforeTheCardFieldsShowsItsReasonExactlyOnce() {
        FlexMessage message = cards.booking(OutboxEventType.PAYMENT_REJECTED, oldBooking("Wrong amount."));

        assertThat(texts(message)).containsSubsequence("Reason", "Wrong amount.");
        assertThat(Collections.frequency(texts(message), "Wrong amount.")).isOne();
    }

    @Test
    void aTripCancellationShowsTheAdminsReasonAndPromisesARefundToAnyoneWhoPaid() {
        FlexMessage message = cards.booking(OutboxEventType.TRIP_CANCELLED, booking("The van has broken down."));

        assertThat(texts(message)).containsSubsequence("AU·VAN · TRIP", "Trip cancelled", "Cancelled",
                "AU-Van has cancelled this trip. If you paid, your fare will be refunded.", "Reason",
                "The van has broken down.", "AU Suvarnabhumi", "Seats", "A1, A2", "Booking ref AUV-261002-ABCD");
        assertThat(Collections.frequency(texts(message), "The van has broken down.")).isOne();
        assertThat(texts(message)).doesNotContain("Pay by");
        assertThat(buttons(message)).isEmpty();
    }

    @Test
    void aRescheduleShowsWhatChangedBesideTheNewDeparture() {
        FlexMessage message = cards.booking(OutboxEventType.TRIP_RESCHEDULED,
                booking("Departure moved from 2 Oct 2026 at 20:00 to 2 Oct 2026 at 22:14."));

        assertThat(texts(message)).containsSubsequence("Departure time changed", "New time", "What changed",
                "Departure moved from 2 Oct 2026 at 20:00 to 2 Oct 2026 at 22:14.", "Departs", "Fri 2 Oct · 22:14");
        assertThat(message.altText()).endsWith("Departure moved from 2 Oct 2026 at 20:00 to 2 Oct 2026 at 22:14.");
        assertThat(buttons(message)).extracting(button -> button.path("action").path("label").asString())
                .containsExactly("View booking");
    }

    @Test
    void departureIsShownInBangkokTimeEvenWhenThatIsTheNextDayInUtc() {
        BookingNotification lateFriday = new BookingNotification("AUV-261002-LATE", "Booked seats A1.",
                "AU", "Asok", OffsetDateTime.parse("2026-10-02T17:30:00Z"), List.of("A1"),
                new BigDecimal("35.00"), OffsetDateTime.parse("2026-10-02T16:59:00Z"));

        List<String> texts = texts(cards.booking(OutboxEventType.BOOKING_CREATED, lateFriday));

        assertThat(texts).contains("Sat 3 Oct · 00:30", "Fri 2 Oct · 23:59");
        assertThat(texts).noneMatch(text -> text.contains("17:30"));
    }

    @Test
    void aBookingRowWrittenBeforeTheCardFieldsIsASimplerBubbleThatStillCarriesItsDetail() {
        FlexMessage message = cards.booking(OutboxEventType.BOOKING_CREATED, oldBooking("Booked seats A1."));

        assertThat(texts(message)).containsSubsequence("Seats held", "Action needed",
                "Your seats are held. Send your payment proof to keep them.", "Booked seats A1.",
                "Booking ref AUV-250101-OLD");
        assertThat(texts(message)).doesNotContain("Departs", "Seats", "Fare", "Pay by");
        assertThat(message.altText()).isEqualTo("AU-Van booking AUV-250101-OLD\n"
                + "Your seats are held. Send your payment proof to keep them.\nBooked seats A1.");
    }

    @Test
    void aStructuredBookingDoesNotRepeatItsDetailSentenceUnderTheRoute() {
        FlexMessage message = cards.booking(OutboxEventType.BOOKING_CANCELLED,
                booking("Cancelled and released seats A1, A2."));

        assertThat(texts(message)).doesNotContain("Cancelled and released seats A1, A2.");
        assertThat(message.altText()).endsWith("Cancelled and released seats A1, A2.");
    }

    @Test
    void anEmptyPayloadStillRendersATitledBubbleWithNoEmptyText() {
        BookingNotification empty = new BookingNotification(null, null, null, null, null, null, null, null);

        FlexMessage message = cards.booking(OutboxEventType.DEPARTURE_REMINDER_24H, empty);

        assertThat(texts(message)).contains("Departs in 24 hours")
                .allMatch(text -> !text.isBlank() && !text.contains("null"));
        assertThat(message.altText()).isEqualTo("AU-Van booking\nYour trip departs in 24 hours.");
    }

    @Test
    void aWaitlistPromotionOffersTheSeatUntilItsDeadline() {
        FlexMessage message = cards.waitlist(OutboxEventType.WAITLIST_PROMOTED, waitlist());

        assertThat(texts(message)).containsSubsequence("AU·VAN · WAITLIST", "A seat is free for you",
                "Held for you", "AU Suvarnabhumi", "Siam Paragon", "Fri 2 Oct · 22:14", "B3", "35.00 THB",
                "Take by", "Fri 2 Oct · 16:00");
        assertThat(texts(message)).noneMatch(text -> text.startsWith("Booking ref"));
        assertThat(message.altText()).startsWith("AU-Van waitlist\nA seat has come free")
                .contains("AU Suvarnabhumi to Siam Paragon").endsWith("Take the seats by 2 Oct 2026 at 09:00.");
    }

    @Test
    void aWaitlistRowWrittenBeforeTheCardFieldsShowsItsTripAndDetail() {
        FlexMessage message = cards.waitlist(OutboxEventType.WAITLIST_PROMOTED, oldWaitlist());

        assertThat(texts(message)).containsSubsequence("A seat is free for you",
                "AU to Asok, departing 2 Oct 2026 at 15:14.", "Take the seats by 2 Oct 2026 at 09:00.");
        assertThat(texts(message)).doesNotContain("Take by", "Departs");
    }

    @Test
    void aLongReasonIsCutInTheAltTextButShownInFullOnTheCard() {
        String reason = "The transfer reference does not match. ".repeat(12).trim();

        FlexMessage message = cards.booking(OutboxEventType.PAYMENT_REJECTED, booking(reason));

        assertThat(reason).hasSizeGreaterThan(400);
        assertThat(message.altText()).hasSize(400).endsWith("…").startsWith("AU-Van booking AUV-261002-ABCD");
        assertThat(texts(message)).contains(reason);
    }

    private static String pillOf(FlexMessage message) {
        JsonNode pill = tree(message).path("contents").path("body").path("contents").get(0)
                .path("contents").get(0);
        return pill.path("contents").get(0).path("text").asString() + " " + pill.path("backgroundColor").asString();
    }
}
