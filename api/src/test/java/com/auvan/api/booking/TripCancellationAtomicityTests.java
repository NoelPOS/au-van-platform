package com.auvan.api.booking;

import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.RefundStatus;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.entity.WaitlistStatus;
import com.auvan.api.booking.service.TripChangeService;
import com.auvan.api.inventory.entity.TripStatus;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.entity.OutboxStatus;
import com.auvan.api.outbox.service.OutboxRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

class TripCancellationAtomicityTests extends TripChangeTestSupport {
    @MockitoSpyBean
    private OutboxRecorder recorder;

    @Autowired
    private TripChangeService changes;

    @Test
    void aFailureHalfwayThroughTheCascadeLeavesNothingCancelled() {
        Booking first = book(0, BookingStatus.CONFIRMED);
        Booking second = book(1, BookingStatus.PAYMENT_UNDER_REVIEW);
        WaitlistEntry waiting = queue(OffsetDateTime.now().minusMinutes(1));
        reminders.schedule(first, OffsetDateTime.now());
        AtomicInteger told = new AtomicInteger();
        doAnswer(invocation -> {
            if (told.incrementAndGet() == 2) {
                throw new IllegalStateException("The outbox is unavailable.");
            }
            return invocation.callRealMethod();
        }).when(recorder).record(eq(OutboxEventType.TRIP_CANCELLED), any(), any(), any(), any());

        assertThatThrownBy(() -> changes.cancel(adminId, trip.getId(), "The van has broken down."))
                .hasMessage("The outbox is unavailable.");

        assertThat(trips.findById(trip.getId()).orElseThrow().getStatus()).isEqualTo(TripStatus.ACTIVE);
        assertThat(reload(first).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(reload(first).getRefundStatus()).isEqualTo(RefundStatus.NONE);
        assertThat(reload(second).getStatus()).isEqualTo(BookingStatus.PAYMENT_UNDER_REVIEW);
        assertThat(eventsOf(first)).hasSize(1);
        assertThat(claims.count()).isEqualTo(2);
        assertThat(waitlist.findById(waiting.getId()).orElseThrow().getStatus()).isEqualTo(WaitlistStatus.WAITING);
        assertThat(outbox.findAll()).allSatisfy(event -> {
            assertThat(event.getEventType()).isNotEqualTo(OutboxEventType.TRIP_CANCELLED);
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        });
    }
}
