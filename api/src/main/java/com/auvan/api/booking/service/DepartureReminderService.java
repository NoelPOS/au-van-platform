package com.auvan.api.booking.service;

import com.auvan.api.booking.entity.Booking;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.notification.dto.BookingNotification;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.repository.OutboxEventRepository;
import com.auvan.api.outbox.service.OutboxRecorder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The two reminders a confirmed booking earns, and their withdrawal when it
 * stops being one.
 *
 * <p>A reminder is not a new mechanism: it is an outbox row whose
 * {@code next_attempt_at} is in the future, which is why ADR-010 dropped the
 * planned {@code reminder_jobs} table. Everything the dispatcher already does —
 * the claim, the lease, the backoff, the dead letter — applies to it unchanged.
 *
 * <p>The timings are ported from the legacy application, which is the only place
 * a validated rule for them exists:
 * {@code src/services/reminder.service.ts:40-43} gives the two offsets,
 * line 77 gives the rule that a job already past is not queued at all,
 * {@code src/models/ReminderJob.ts:46}'s {@code unique (bookingId, type)} is
 * reproduced as the unique {@code dedupe_key}, and
 * {@code reminder.service.ts:107-118}'s {@code cancelForBooking} is
 * {@link #cancel}. The legacy's third mode, a daily batch at 01:00 UTC, is
 * deliberately not ported: it exists to make one Vercel cron slot cover every
 * booking, a constraint this API does not have, and an hour's warning is worth
 * more to a student than a message at one in the morning.
 *
 * <p><strong>Not {@code @Transactional}</strong>, and for the reason
 * {@link OutboxRecorder} states: both methods join the caller's transaction, so
 * an approval that rolls back schedules nothing and a cancellation that rolls
 * back withdraws nothing.
 */
@Service
public class DepartureReminderService {
    /** Reproduces the legacy's {@code departure_24h} and {@code departure_1h}. */
    private static final List<Offset> OFFSETS = List.of(
            new Offset(OutboxEventType.DEPARTURE_REMINDER_24H, Duration.ofHours(24)),
            new Offset(OutboxEventType.DEPARTURE_REMINDER_1H, Duration.ofHours(1)));

    private static final DateTimeFormatter DEPARTURE =
            DateTimeFormatter.ofPattern("d MMM yyyy 'at' HH:mm", Locale.ENGLISH);

    private static final String NO_LONGER_ELIGIBLE = "The booking is no longer eligible for a reminder.";

    private final OutboxRecorder outbox;
    private final OutboxEventRepository events;

    public DepartureReminderService(OutboxRecorder outbox, OutboxEventRepository events) {
        this.outbox = outbox;
        this.events = events;
    }

    /**
     * Schedules whichever of the two reminders is still ahead of the booking.
     *
     * <p>A reminder whose moment has already passed is <strong>not queued</strong>,
     * which is the legacy's own rule and the one that matters: a booking
     * approved two hours before departure must not immediately fire a message
     * announcing that the trip is twenty-four hours away. Queueing it and
     * letting the dispatcher decide would be exactly that bug, because a row
     * due in the past is a row that is due now.
     *
     * @return how many reminders this call queued, which is two, one, or none
     */
    public int schedule(Booking booking, OffsetDateTime now) {
        Trip trip = booking.getTrip();
        String detail = trip.getRoute().getOrigin() + " to " + trip.getRoute().getDestination()
                + ", departing " + DEPARTURE.format(trip.getDepartureAt()) + ".";
        int queued = 0;
        for (Offset offset : OFFSETS) {
            OffsetDateTime dueAt = trip.getDepartureAt().minus(offset.before());
            if (!dueAt.isAfter(now)) {
                continue;
            }
            if (outbox.schedule(offset.type(), booking.getId(), booking.getUserId(),
                    new BookingNotification(booking.getReference(), detail),
                    dedupeKeyFor(booking.getId(), offset.type()), dueAt, now) != null) {
                queued++;
            }
        }
        return queued;
    }

    /**
     * Withdraws a booking's unsent reminders, because it is not departing.
     *
     * <p>Called from cancellation and from expiry, and it has to be: a reminder
     * is scheduled hours or days before it fires, so without this a student who
     * cancelled yesterday is told this afternoon that their trip leaves in an
     * hour. The rows are marked {@code DEAD} rather than deleted, so a dead
     * letter still records that the reminder existed and why it never went.
     *
     * @return how many reminders this call withdrew
     */
    public int cancel(UUID bookingId, OffsetDateTime now) {
        return events.cancelScheduled(bookingId, now, NO_LONGER_ELIGIBLE);
    }

    /**
     * {@code "<bookingId>:<type>"}, which is the legacy's
     * {@code unique (bookingId, type)} written as one column. It is unique
     * across {@code outbox_events}, and that constraint — not this string — is
     * what makes scheduling idempotent.
     */
    private static String dedupeKeyFor(UUID bookingId, OutboxEventType type) {
        return bookingId + ":" + type.name();
    }

    /** One reminder: what it is called, and how far before departure it fires. */
    private record Offset(OutboxEventType type, Duration before) { }
}
