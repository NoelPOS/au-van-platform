package com.auvan.api.booking;

import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingEvent;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.RefundStatus;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.entity.WaitlistStatus;
import com.auvan.api.inventory.entity.TripStatus;
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.entity.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripCancellationIntegrationTests extends TripChangeTestSupport {
    @Test
    void cancellingATripCancelsEveryActiveBookingReleasesItsSeatsAndOwesWhatMayHaveBeenPaid() throws Exception {
        Booking unpaid = book(0, BookingStatus.PENDING_PAYMENT);
        Booking underReview = book(1, BookingStatus.PAYMENT_UNDER_REVIEW);
        Booking confirmed = book(2, BookingStatus.CONFIRMED);
        Booking cancelledBefore = book(3, BookingStatus.CANCELLED);

        cancel("{\"reason\":\"  The van has broken down.  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("The van has broken down."));

        assertThat(reload(unpaid)).satisfies(booking -> {
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(booking.getRefundStatus()).isEqualTo(RefundStatus.NONE);
            assertThat(booking.getPaymentDeadlineAt()).isNull();
        });
        assertThat(reload(underReview).getRefundStatus()).isEqualTo(RefundStatus.DUE);
        assertThat(reload(confirmed).getRefundStatus()).isEqualTo(RefundStatus.DUE);
        assertThat(eventsOf(cancelledBefore)).extracting(BookingEvent::getEventType)
                .doesNotContain(BookingEventType.TRIP_CANCELLED);
        assertThat(claims.count()).isZero();
        assertThat(trips.findById(trip.getId()).orElseThrow().getStatus()).isEqualTo(TripStatus.CANCELLED);
    }

    @Test
    void eachCancelledBookingRecordsWhyInItsHistoryAndTellsItsStudentOnce() throws Exception {
        Booking unpaid = book(0, BookingStatus.PENDING_PAYMENT);
        Booking confirmed = book(1, BookingStatus.CONFIRMED);
        book(2, BookingStatus.CANCELLED);

        cancel("{\"reason\":\"The van has broken down.\"}").andExpect(status().isOk());

        assertThat(eventsOf(confirmed)).last().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo(BookingEventType.TRIP_CANCELLED);
            assertThat(event.getActorUserId()).isEqualTo(adminId);
            assertThat(event.getDetail()).isEqualTo("Trip cancelled and seats A2 released. A refund of 35.00 THB "
                    + "is due. Reason: The van has broken down.");
        });
        assertThat(tripCancelledMessages()).extracting(OutboxEvent::getAggregateId)
                .containsExactlyInAnyOrder(unpaid.getId(), confirmed.getId());
        assertThat(tripCancelledMessages()).allSatisfy(event ->
                assertThat(event.getPayload()).contains("\"detail\":\"The van has broken down.\""));
    }

    @Test
    void aConfirmedBookingsUnsentRemindersAreWithdrawn() throws Exception {
        Booking confirmed = book(0, BookingStatus.CONFIRMED);
        assertThat(reminders.schedule(confirmed, OffsetDateTime.now())).isEqualTo(2);

        cancel("{\"reason\":\"The van has broken down.\"}").andExpect(status().isOk());

        assertThat(outbox.findAll()).filteredOn(event -> event.getDedupeKey() != null)
                .hasSize(2)
                .allSatisfy(event -> assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD));
    }

    @Test
    void waitingAndPromotedStudentsAreTakenOffTheWaitlistAndTold() throws Exception {
        WaitlistEntry waiting = queue(OffsetDateTime.now().minusMinutes(3));
        WaitlistEntry promoted = queue(OffsetDateTime.now().minusMinutes(2));
        promoted.promote(UUID.randomUUID(), OffsetDateTime.now().plusMinutes(20), OffsetDateTime.now());
        waitlist.save(promoted);
        WaitlistEntry withdrawn = queue(OffsetDateTime.now().minusMinutes(1));
        withdrawn.withdraw(OffsetDateTime.now());
        waitlist.save(withdrawn);

        cancel("{\"reason\":\"No driver is available.\"}").andExpect(status().isOk());

        assertThat(waitlist.findById(waiting.getId()).orElseThrow().getStatus()).isEqualTo(WaitlistStatus.CANCELLED);
        assertThat(waitlist.findById(promoted.getId()).orElseThrow().getStatus()).isEqualTo(WaitlistStatus.CANCELLED);
        assertThat(waitlist.findById(withdrawn.getId()).orElseThrow().getStatus())
                .isEqualTo(WaitlistStatus.WITHDRAWN);
        assertThat(tripCancelledMessages()).extracting(OutboxEvent::getAggregateId)
                .containsExactlyInAnyOrder(waiting.getId(), promoted.getId());
    }

    @Test
    void aReasonIsRequired() throws Exception {
        Booking confirmed = book(0, BookingStatus.CONFIRMED);

        cancel("{\"reason\":\"   \"}").andExpect(status().isBadRequest());
        cancel("{}").andExpect(status().isBadRequest());

        assertUnchanged(confirmed);
    }

    @Test
    void aReasonLongerThanThreeHundredCharactersIsRefused() throws Exception {
        Booking confirmed = book(0, BookingStatus.CONFIRMED);

        cancel("{\"reason\":\"" + "x".repeat(301) + "\"}").andExpect(status().isBadRequest());
        cancel("{\"reason\":\"" + "x".repeat(300) + "\"}").andExpect(status().isOk());

        assertThat(reload(confirmed).getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    void aCancelledTripCannotBeCancelledAgain() throws Exception {
        cancel("{\"reason\":\"The van has broken down.\"}").andExpect(status().isOk());

        cancel("{\"reason\":\"Again.\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("trip_cancelled"));
        assertThat(trips.findById(trip.getId()).orElseThrow().getCancellationReason())
                .isEqualTo("The van has broken down.");
    }

    @Test
    void aTripThatHasDepartedCannotBeCancelled() throws Exception {
        Booking confirmed = book(0, BookingStatus.CONFIRMED);
        departIn(-5);

        cancel("{\"reason\":\"Too late.\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("trip_departed"));

        assertUnchanged(confirmed);
    }

    @Test
    void anUnknownTripIsNotFound() throws Exception {
        mockMvc.perform(admin(post("/api/v1/admin/trips/" + UUID.randomUUID() + "/cancel"), "{\"reason\":\"Gone.\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("trip_not_found"));
    }

    @Test
    void aStudentCannotCancelATrip() throws Exception {
        Booking confirmed = book(0, BookingStatus.CONFIRMED);

        mockMvc.perform(asStudent(post("/api/v1/admin/trips/" + trip.getId() + "/cancel"), "{\"reason\":\"Mine.\"}"))
                .andExpect(status().isForbidden());

        assertUnchanged(confirmed);
    }

    @Test
    void aHoldOnACancelledTripCannotBecomeABooking() throws Exception {
        String holdId = holdAsStudent(0);
        cancel("{\"reason\":\"The van has broken down.\"}").andExpect(status().isOk());

        confirmAsStudent(holdId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("trip_not_available"));
        assertThat(bookings.count()).isZero();
    }

    private ResultActions cancel(String body) throws Exception {
        return mockMvc.perform(admin(post("/api/v1/admin/trips/" + trip.getId() + "/cancel"), body));
    }

    private void assertUnchanged(Booking booking) {
        assertThat(reload(booking).getStatus()).isEqualTo(booking.getStatus());
        assertThat(reload(booking).getRefundStatus()).isEqualTo(RefundStatus.NONE);
        assertThat(claims.findByBookingId(booking.getId())).hasSize(1);
        assertThat(trips.findById(trip.getId()).orElseThrow().getStatus()).isEqualTo(TripStatus.ACTIVE);
        assertThat(tripCancelledMessages()).isEmpty();
    }

    private List<OutboxEvent> tripCancelledMessages() {
        return outbox.findAll().stream().filter(event -> event.getEventType() == OutboxEventType.TRIP_CANCELLED)
                .toList();
    }
}
