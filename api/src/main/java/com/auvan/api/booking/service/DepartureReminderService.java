package com.auvan.api.booking.service;

import com.auvan.api.booking.entity.Booking;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.repository.OutboxEventRepository;
import com.auvan.api.outbox.service.OutboxRecorder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

// Not @Transactional: joins the caller's, so a rolled-back approval schedules nothing.
@Service
public class DepartureReminderService {
    private static final List<Offset> OFFSETS = List.of(
            new Offset(OutboxEventType.DEPARTURE_REMINDER_24H, Duration.ofHours(24)),
            new Offset(OutboxEventType.DEPARTURE_REMINDER_1H, Duration.ofHours(1)));

    private static final String NO_LONGER_ELIGIBLE = "The booking is no longer eligible for a reminder.";

    private final OutboxRecorder outbox;
    private final OutboxEventRepository events;

    public DepartureReminderService(OutboxRecorder outbox, OutboxEventRepository events) {
        this.outbox = outbox;
        this.events = events;
    }

    public int schedule(Booking booking, OffsetDateTime now) {
        Trip trip = booking.getTrip();
        String detail = trip.getRoute().getOrigin() + " to " + trip.getRoute().getDestination()
                + ", departing " + BookingNotifications.moment(trip.getDepartureAt()) + ".";
        int queued = 0;
        for (Offset offset : OFFSETS) {
            OffsetDateTime dueAt = trip.getDepartureAt().minus(offset.before());
            if (!dueAt.isAfter(now)) {
                continue;
            }
            if (outbox.schedule(offset.type(), booking.getId(), booking.getUserId(),
                    BookingNotifications.of(booking, detail),
                    dedupeKeyFor(booking.getId(), offset.type()), dueAt, now) != null) {
                queued++;
            }
        }
        return queued;
    }

    public int cancel(UUID bookingId, OffsetDateTime now) {
        return events.cancelScheduled(bookingId, now, NO_LONGER_ELIGIBLE);
    }

    private static String dedupeKeyFor(UUID bookingId, OutboxEventType type) {
        return bookingId + ":" + type.name();
    }

    private record Offset(OutboxEventType type, Duration before) { }
}
