package com.auvan.api.booking;

import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.entity.WaitlistStatus;
import com.auvan.api.inventory.entity.Trip;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripOperationsIntegrationTests extends OperationsTestSupport {
    @Test
    void theTripViewReportsBookingsByStatusAndTheQueueWithItsPromotions() throws Exception {
        book(trip, firstStudent, "AUV-OP-0001", BookingStatus.CONFIRMED);
        book(trip, secondStudent, "AUV-OP-0002", BookingStatus.PENDING_PAYMENT);
        book(trip, thirdStudent, "AUV-OP-0003", BookingStatus.CANCELLED);
        book(trip, firstStudent, "AUV-OP-0004", BookingStatus.CANCELLED);
        UUID waiting = queue(trip, firstStudent, 1, OffsetDateTime.now().minusMinutes(30));
        UUID promoted = queue(trip, secondStudent, 2, OffsetDateTime.now().minusMinutes(20));
        UUID withdrawn = queue(trip, thirdStudent, 1, OffsetDateTime.now().minusMinutes(10));
        UUID holdId = UUID.randomUUID();
        OffsetDateTime promotionExpiresAt = OffsetDateTime.now().plusMinutes(25);
        promote(promoted, holdId, promotionExpiresAt);
        withdraw(withdrawn);

        tripView(trip)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tripId").value(trip.getId().toString()))
                .andExpect(jsonPath("$.origin").value("AU"))
                .andExpect(jsonPath("$.destination").value("Asok"))
                .andExpect(jsonPath("$.tripStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.totalSeats").value(2))
                .andExpect(jsonPath("$.bookingsByStatus.length()").value(5))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'CONFIRMED')].count").value(1))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'PENDING_PAYMENT')].count").value(1))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'CANCELLED')].count").value(2))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'PAYMENT_UNDER_REVIEW')].count").value(0))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'PAYMENT_REJECTED')].count").value(0))
                .andExpect(jsonPath("$.waitlist.length()").value(3))
                .andExpect(jsonPath("$.waitlist[0].entryId").value(waiting.toString()))
                .andExpect(jsonPath("$.waitlist[0].status").value("WAITING"))
                .andExpect(jsonPath("$.waitlist[0].position").value(1))
                .andExpect(jsonPath("$.waitlist[0].seatsWanted").value(1))
                .andExpect(jsonPath("$.waitlist[0].promotionHoldId").doesNotExist())
                .andExpect(jsonPath("$.waitlist[1].entryId").value(promoted.toString()))
                .andExpect(jsonPath("$.waitlist[1].status").value("PROMOTED"))
                .andExpect(jsonPath("$.waitlist[1].position").value(2))
                .andExpect(jsonPath("$.waitlist[1].displayName").value("Malee K."))
                .andExpect(jsonPath("$.waitlist[1].promotionHoldId").value(holdId.toString()))
                .andExpect(jsonPath("$.waitlist[1].promotionExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.waitlist[2].entryId").value(withdrawn.toString()))
                .andExpect(jsonPath("$.waitlist[2].status").value("WITHDRAWN"))
                .andExpect(jsonPath("$.waitlist[2].position").doesNotExist());
    }

    @Test
    void claimedSeatsCountsOnlyTheClaimsThatStillBlockTheirSeat() throws Exception {
        UUID holder = users.save(new AppUser("Uholder", "Holding Student")).getId();
        claims.save(new SeatClaim(trip.getSeats().get(0), holder, UUID.randomUUID(),
                OffsetDateTime.now().plusHours(2)));
        claims.save(new SeatClaim(trip.getSeats().get(1), holder, UUID.randomUUID(),
                OffsetDateTime.now().minusMinutes(1)));

        tripView(trip)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSeats").value(2))
                .andExpect(jsonPath("$.claimedSeats").value(1));
    }

    @Test
    void theTripViewReportsThisTripsBookingsAndQueueAndNotAnotherTrips() throws Exception {
        book(trip, firstStudent, "AUV-OP-0005", BookingStatus.CONFIRMED);
        book(otherTrip, secondStudent, "AUV-OP-0006", BookingStatus.CONFIRMED);
        book(otherTrip, thirdStudent, "AUV-OP-0007", BookingStatus.PENDING_PAYMENT);
        UUID mine = queue(trip, firstStudent, 1, OffsetDateTime.now().minusMinutes(5));
        queue(otherTrip, secondStudent, 1, OffsetDateTime.now().minusMinutes(4));
        queue(otherTrip, thirdStudent, 1, OffsetDateTime.now().minusMinutes(3));

        tripView(trip)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'CONFIRMED')].count").value(1))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'PENDING_PAYMENT')].count").value(0))
                .andExpect(jsonPath("$.waitlist.length()").value(1))
                .andExpect(jsonPath("$.waitlist[0].entryId").value(mine.toString()));

        tripView(otherTrip)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'CONFIRMED')].count").value(1))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'PENDING_PAYMENT')].count").value(1))
                .andExpect(jsonPath("$.waitlist.length()").value(2));
    }

    @Test
    void aTripWithNothingHappeningReportsEmptyRatherThanFailing() throws Exception {
        tripView(trip)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimedSeats").value(0))
                .andExpect(jsonPath("$.bookingsByStatus.length()").value(5))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'PENDING_PAYMENT')].count").value(0))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'CONFIRMED')].count").value(0))
                .andExpect(jsonPath("$.bookingsByStatus[?(@.status == 'CANCELLED')].count").value(0))
                .andExpect(jsonPath("$.waitlist.length()").value(0));

        mockMvc.perform(get("/api/v1/admin/operations/dead-letters")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anUnknownTripIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/admin/operations/trips/" + UUID.randomUUID())
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("trip_not_found"));
    }

    private ResultActions tripView(Trip subject) throws Exception {
        return mockMvc.perform(get(tripPath(subject)).header("Authorization", bearer(adminToken)));
    }

    private void book(Trip subject, UUID userId, String reference, BookingStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        Booking booking = new Booking(subject, userId, reference, "Somchai P.", "0812345678",
                new BigDecimal("35.00"), now.plusMinutes(30), now);
        switch (status) {
            case PENDING_PAYMENT -> { }
            case PAYMENT_UNDER_REVIEW -> booking.markPaymentUnderReview(now);
            case PAYMENT_REJECTED -> booking.markPaymentRejected(now.plusMinutes(30), now);
            case CONFIRMED -> booking.confirm(now);
            case CANCELLED -> booking.cancel(now);
        }
        bookings.save(booking);
    }

    private UUID queue(Trip subject, UUID userId, int seatsWanted, OffsetDateTime joinedAt) {
        return waitlist.saveAndFlush(new WaitlistEntry(subject, userId, seatsWanted, joinedAt)).getId();
    }

    private void promote(UUID entryId, UUID holdId, OffsetDateTime expiresAt) {
        jdbc.update("update waitlist_entries set status = ?, promotion_hold_id = ?, promotion_expires_at = ?, "
                        + "updated_at = ? where id = ?",
                WaitlistStatus.PROMOTED.name(), holdId, expiresAt, OffsetDateTime.now(), entryId);
    }

    private void withdraw(UUID entryId) {
        WaitlistEntry entry = waitlist.findById(entryId).orElseThrow();
        entry.withdraw(OffsetDateTime.now());
        waitlist.saveAndFlush(entry);
    }
}
