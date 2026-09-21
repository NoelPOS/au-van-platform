package com.auvan.api.booking.service;

import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.BookingSeat;
import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.notification.dto.BookingNotification;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.service.OutboxRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Bookings: create, read, and cancel.
 *
 * <p><strong>{@link #create} carries no {@code @Transactional}, and must not.</strong>
 * It orchestrates three transactions — the pre-check, the write, and the replay
 * — and each has to be its own. Hibernate marks a transaction rollback-only at
 * flush, when it converts a constraint violation, so by the time anything is
 * caught the surrounding transaction is already dead and the replay read could
 * not run in it. The absence of an annotation is invisible in review, which is
 * why it is written down here.
 */
@Service
public class BookingService {
    /** The stored scope of an idempotency key; {@code endpoint} is V3's column name for it. */
    static final String CREATE_ENDPOINT = "POST /api/v1/bookings";

    private final BookingWriter writer;
    private final IdempotencyService idempotency;
    private final BookingRepository bookings;
    private final SeatClaimRepository claims;
    private final OutboxRecorder outbox;
    private final DepartureReminderService reminders;

    public BookingService(BookingWriter writer, IdempotencyService idempotency, BookingRepository bookings,
                          SeatClaimRepository claims, OutboxRecorder outbox, DepartureReminderService reminders) {
        this.writer = writer;
        this.idempotency = idempotency;
        this.bookings = bookings;
        this.claims = claims;
        this.outbox = outbox;
        this.reminders = reminders;
    }

    public IdempotencyService.StoredResponse create(UUID userId, String key, CreateBookingRequest request) {
        String requestHash = idempotency.fingerprint(request);
        Optional<IdempotencyService.StoredResponse> replay =
                idempotency.find(userId, CREATE_ENDPOINT, key, requestHash);
        if (replay.isPresent()) {
            return replay.get();
        }
        try {
            return writer.create(userId, CREATE_ENDPOINT, key, requestHash, request);
        } catch (RuntimeException failure) {
            // A duplicate of this very request may have committed while this one
            // waited on the hold's row lock, in which case the loser's own
            // failure is beside the point and the winner's stored response is
            // the answer. Nothing else can be stored under this key and hash.
            return idempotency.find(userId, CREATE_ENDPOINT, key, requestHash).orElseThrow(() -> failure);
        }
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> list(UUID userId) {
        return bookings.findByUserIdOrderByCreatedAtDesc(userId).stream().map(BookingResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID userId, UUID bookingId) {
        return BookingResponse.from(load(userId, bookingId));
    }

    /**
     * Cancels a booking and frees its seats. There is no cutoff window: the
     * legacy application refuses only an already-cancelled booking, and its
     * two-hour rule belongs to rescheduling, which is out of scope.
     *
     * <p>The booking's row is locked first, and everything below decides from
     * what the lock returned. Cancellation is a read-then-write on
     * {@code status} racing the administrator's review (#52): without the lock
     * an approval committing in between leaves a {@code CONFIRMED} booking
     * whose {@code seat_claims} this method has already deleted. Owner-scoped,
     * because cancelling is the student's own action.
     */
    @Transactional
    public BookingResponse cancel(UUID userId, UUID bookingId) {
        OffsetDateTime now = OffsetDateTime.now();
        Booking booking = bookings.lockByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> Problems.notFound("booking_not_found", "Booking not found."));
        if (booking.isCancelled()) {
            throw Problems.conflict("booking_already_cancelled", "That booking has already been cancelled.");
        }
        booking.cancel(now);
        String detail = "Cancelled and released seats " + labelsOf(booking) + ".";
        booking.recordEvent(BookingEventType.CANCELLED, detail, userId, now);
        // Recorded here, before the flush, for the same reason the flush exists:
        // deleteByBookingId clears the persistence context, and an outbox insert
        // that has not been flushed by then is discarded silently. The booking
        // would commit cancelled with nothing saying the student was owed a
        // message, which is exactly the divergence the outbox exists to prevent.
        outbox.record(OutboxEventType.BOOKING_CANCELLED, booking.getId(), booking.getUserId(),
                new BookingNotification(booking.getReference(), detail), now);
        // Load-bearing, and it must come before the delete. deleteByBookingId
        // clears the persistence context, which would discard the cancellation
        // and the event above without raising anything at all.
        bookings.flush();
        // Built while the booking is still managed, for the same reason.
        BookingResponse response = BookingResponse.from(booking);
        claims.deleteByBookingId(booking.getId());
        // A cancelled booking is not departing, so its scheduled reminders must
        // not fire. Last, and after the claim delete on purpose: this is a bulk
        // update, the delete has already cleared the persistence context, and
        // nothing below reads a managed entity. It is scoped to rows carrying a
        // dedupe key, so the BOOKING_CANCELLED row recorded above — which the
        // student does still need — is untouched.
        reminders.cancel(bookingId, now);
        return response;
    }

    private Booking load(UUID userId, UUID bookingId) {
        // Another student's booking answers exactly as one that does not exist.
        return bookings.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> Problems.notFound("booking_not_found", "Booking not found."));
    }

    private static String labelsOf(Booking booking) {
        return booking.getSeats().stream().map(BookingSeat::getTripSeat).map(TripSeat::getLabel).sorted()
                .collect(Collectors.joining(", "));
    }
}
