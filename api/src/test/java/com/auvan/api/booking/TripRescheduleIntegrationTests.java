package com.auvan.api.booking;

import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripStatus;
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.entity.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TripRescheduleIntegrationTests extends TripChangeTestSupport {
    @Test
    void movingATripTellsEveryActivePassengerTheOldAndTheNewTime() throws Exception {
        Booking unpaid = book(0, BookingStatus.PENDING_PAYMENT);
        Booking confirmed = book(1, BookingStatus.CONFIRMED);
        book(2, BookingStatus.CANCELLED);
        OffsetDateTime from = OffsetDateTime.parse("2030-01-15T08:00:00Z");
        departAt(from);

        update("{\"departureAt\":\"2030-01-15T10:30:00Z\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        String detail = "Departure moved from 15 Jan 2030 at 15:00 to 15 Jan 2030 at 17:30.";
        assertThat(messages(OutboxEventType.TRIP_RESCHEDULED)).extracting(OutboxEvent::getAggregateId)
                .containsExactlyInAnyOrder(unpaid.getId(), confirmed.getId());
        assertThat(messages(OutboxEventType.TRIP_RESCHEDULED)).allSatisfy(event -> assertThat(event.getPayload())
                .contains("\"detail\":\"" + detail + "\"").contains("2030-01-15T10:30"));
        assertThat(eventsOf(confirmed)).last().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo(BookingEventType.TRIP_RESCHEDULED);
            assertThat(event.getDetail()).isEqualTo(detail);
            assertThat(event.getActorUserId()).isEqualTo(adminId);
        });
    }

    @Test
    void aConfirmedPassengersRemindersFollowTheNewTime() throws Exception {
        Booking confirmed = book(0, BookingStatus.CONFIRMED);
        reminders.schedule(confirmed, OffsetDateTime.now());
        OffsetDateTime later = trip.getDepartureAt().plusHours(3).truncatedTo(ChronoUnit.SECONDS);

        update("{\"departureAt\":\"" + later + "\"}").andExpect(status().isOk());

        List<OutboxEvent> scheduled = outbox.findAll().stream().filter(event -> event.getDedupeKey() != null)
                .toList();
        assertThat(scheduled).allSatisfy(event -> assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING));
        assertThat(scheduled).extracting(event -> event.getNextAttemptAt().toInstant())
                .containsExactlyInAnyOrder(later.minusHours(24).toInstant(), later.minusHours(1).toInstant());
    }

    @Test
    void anUnpaidBookingMustStillBePaidAnHourBeforeAnEarlierDeparture() throws Exception {
        Booking unpaid = book(0, BookingStatus.PENDING_PAYMENT);
        OffsetDateTime sooner = OffsetDateTime.now().plusMinutes(100).truncatedTo(ChronoUnit.SECONDS);

        update("{\"departureAt\":\"" + sooner + "\"}").andExpect(status().isOk());

        assertThat(reload(unpaid).getPaymentDeadlineAt())
                .isCloseTo(sooner.minusHours(1), within(1, ChronoUnit.SECONDS));
    }

    @Test
    void aNewTimeAfterBookingWouldAlreadyHaveClosedIsRefused() throws Exception {
        Booking confirmed = book(0, BookingStatus.CONFIRMED);
        OffsetDateTime tooSoon = OffsetDateTime.now().plusMinutes(80).truncatedTo(ChronoUnit.SECONDS);

        update("{\"departureAt\":\"" + tooSoon + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("departure_too_soon"));

        assertUntouched(confirmed);
    }

    @Test
    void aTripThatHasDepartedCannotBeChanged() throws Exception {
        Booking confirmed = book(0, BookingStatus.CONFIRMED);
        departIn(-5);

        update("{\"departureAt\":\"" + OffsetDateTime.now().plusDays(1).truncatedTo(ChronoUnit.SECONDS) + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("trip_departed"));

        assertThat(messages(OutboxEventType.TRIP_RESCHEDULED)).isEmpty();
        assertThat(eventsOf(confirmed)).hasSize(1);
    }

    @Test
    void theGenericUpdateCannotCancelATrip() throws Exception {
        Booking confirmed = book(0, BookingStatus.CONFIRMED);

        update("{\"status\":\"CANCELLED\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("trip_cancellation_needs_reason"));

        assertUntouched(confirmed);
        assertThat(reload(confirmed).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void aCancelledTripCannotBeReactivatedOrMoved() throws Exception {
        mockMvc.perform(admin(post("/api/v1/admin/trips/" + trip.getId() + "/cancel"), "{\"reason\":\"Broken.\"}"))
                .andExpect(status().isOk());

        update("{\"status\":\"ACTIVE\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("trip_cancelled"));
        update("{\"departureAt\":\"" + trip.getDepartureAt().plusHours(1).truncatedTo(ChronoUnit.SECONDS) + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("trip_cancelled"));

        assertThat(trips.findById(trip.getId()).orElseThrow().getStatus()).isEqualTo(TripStatus.CANCELLED);
    }

    @Test
    void anUpdateThatKeepsTheTimeTellsNobody() throws Exception {
        book(0, BookingStatus.CONFIRMED);

        update("{\"status\":\"ACTIVE\"}").andExpect(status().isOk());
        update("{\"departureAt\":\"" + trip.getDepartureAt() + "\"}").andExpect(status().isOk());

        assertThat(messages(OutboxEventType.TRIP_RESCHEDULED)).isEmpty();
    }

    @Test
    void aTripCannotBeMovedOntoAnotherTripOfTheSameVan() throws Exception {
        Booking confirmed = book(0, BookingStatus.CONFIRMED);
        OffsetDateTime taken = trip.getDepartureAt().plusHours(5).truncatedTo(ChronoUnit.SECONDS);
        trips.save(new Trip(trip.getRoute(), trip.getVehicle(), taken));

        update("{\"departureAt\":\"" + taken + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("vehicle_already_scheduled"));

        assertUntouched(confirmed);
    }

    @Test
    void aStudentCannotMoveATrip() throws Exception {
        Booking confirmed = book(0, BookingStatus.CONFIRMED);

        mockMvc.perform(asStudent(put("/api/v1/admin/trips/" + trip.getId()),
                        "{\"departureAt\":\"" + trip.getDepartureAt().plusHours(1) + "\"}"))
                .andExpect(status().isForbidden());

        assertUntouched(confirmed);
    }

    private void assertUntouched(Booking booking) {
        assertThat(trips.findById(trip.getId()).orElseThrow().getDepartureAt())
                .isAtSameInstantAs(trip.getDepartureAt());
        assertThat(eventsOf(booking)).hasSize(1);
        assertThat(messages(OutboxEventType.TRIP_RESCHEDULED)).isEmpty();
    }

    private ResultActions update(String body) throws Exception {
        return mockMvc.perform(admin(put("/api/v1/admin/trips/" + trip.getId()), body));
    }

    private List<OutboxEvent> messages(OutboxEventType type) {
        return outbox.findAll().stream().filter(event -> event.getEventType() == type).toList();
    }
}
