package com.auvan.api.booking;

import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.VanRoute;
import com.auvan.api.inventory.entity.Vehicle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where a booking starts and how a payment proof moves it. ADR-009 made the
 * review the only path to {@code CONFIRMED}, so creation producing a confirmed
 * booking is the regression this class exists to catch.
 */
class BookingLifecycleTests {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-21T10:00:00Z");

    @Test
    void aNewBookingWaitsForPaymentRatherThanBeingConfirmed() {
        Booking booking = newBooking();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
        assertThat(booking.isAwaitingPaymentProof()).isTrue();
    }

    @Test
    void aSubmittedProofPutsTheBookingUnderReview() {
        Booking booking = newBooking();

        booking.markPaymentUnderReview(NOW.plusMinutes(3));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW);
        assertThat(booking.getUpdatedAt()).isEqualTo(NOW.plusMinutes(3));
        // The second submission #52 allows arrives from a rejection, not from
        // a booking already sitting in front of an administrator.
        assertThat(booking.isAwaitingPaymentProof()).isFalse();
    }

    @Test
    void aCancelledBookingAcceptsNoProof() {
        Booking booking = newBooking();

        booking.cancel(NOW.plusMinutes(1));

        assertThat(booking.isAwaitingPaymentProof()).isFalse();
    }

    private static Booking newBooking() {
        VanRoute route = new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45);
        SeatLayout layout = new SeatLayout("Layout VAN-01", List.of(new SeatLayoutSeat("A1", 1, 1)));
        Trip trip = new Trip(route, new Vehicle("VAN-01", "Toyota Commuter", layout), NOW.plusDays(1));
        return new Booking(trip, UUID.randomUUID(), "AUV-260921-7KQ2M4XR", "Somchai P.", "0812345678",
                new BigDecimal("35.00"), NOW);
    }
}
