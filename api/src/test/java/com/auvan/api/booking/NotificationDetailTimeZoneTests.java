package com.auvan.api.booking;

import com.auvan.api.booking.config.BookingProperties;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.booking.service.DepartureReminderService;
import com.auvan.api.booking.service.SeatAvailabilityService;
import com.auvan.api.booking.service.WaitlistPromotionWriter;
import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.VanRoute;
import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.notification.dto.BookingNotification;
import com.auvan.api.notification.dto.WaitlistNotification;
import com.auvan.api.outbox.repository.OutboxEventRepository;
import com.auvan.api.outbox.service.OutboxRecorder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDetailTimeZoneTests {
    // 17:30 UTC on Friday is 00:30 on Saturday in Bangkok: a UTC rendering is wrong in both day and hour.
    private static final OffsetDateTime DEPARTURE = OffsetDateTime.parse("2026-10-02T17:30:00Z");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-28T03:00:00Z");

    private final OutboxRecorder outbox = mock(OutboxRecorder.class);
    private final Trip trip = new Trip(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45),
            new Vehicle("VAN-TZ", "Toyota Commuter", new SeatLayout("TZ", List.of(new SeatLayoutSeat("A1", 1, 1)))),
            DEPARTURE);

    @Test
    void aReminderDescribesTheDepartureInBangkokTime() {
        Booking booking = new Booking(trip, UUID.randomUUID(), "AUV-260928-TZ", "Somchai P.", "0812345678",
                new BigDecimal("35.00"), null, NOW);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);

        new DepartureReminderService(outbox, mock(OutboxEventRepository.class)).schedule(booking, NOW);

        verify(outbox, times(2))
                .schedule(any(), any(), any(), payload.capture(), anyString(), any(), any());
        assertThat(payload.getAllValues()).allSatisfy(captured -> assertThat(((BookingNotification) captured)
                .detail()).isEqualTo("AU to Asok, departing 3 Oct 2026 at 00:30."));
    }

    @Test
    void aLapsedWaitlistOfferDescribesItsTripAndDeadlineInBangkokTime() {
        WaitlistEntry entry = new WaitlistEntry(trip, UUID.randomUUID(), 1, NOW);
        entry.promote(UUID.randomUUID(), OffsetDateTime.parse("2026-10-01T18:15:00Z"), NOW);
        WaitlistEntryRepository entries = mock(WaitlistEntryRepository.class);
        when(entries.lockById(any())).thenReturn(Optional.of(entry));
        SeatClaimRepository claims = mock(SeatClaimRepository.class);
        when(claims.findByHoldId(any())).thenReturn(List.of());
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);

        new WaitlistPromotionWriter(entries, claims, mock(SeatAvailabilityService.class), outbox,
                mock(BookingProperties.class)).resolve(UUID.randomUUID(), OffsetDateTime.parse("2026-10-01T19:00:00Z"));

        verify(outbox).record(any(), any(), any(), payload.capture(), any());
        WaitlistNotification notification = (WaitlistNotification) payload.getValue();
        assertThat(notification.trip()).isEqualTo("AU to Asok, departing 3 Oct 2026 at 00:30.");
        assertThat(notification.detail()).isEqualTo("The offer ran out on 2 Oct 2026 at 01:15.");
    }
}
