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

    public BookingService(BookingWriter writer, IdempotencyService idempotency, BookingRepository bookings,
                          SeatClaimRepository claims) {
        this.writer = writer;
        this.idempotency = idempotency;
        this.bookings = bookings;
        this.claims = claims;
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
     */
    @Transactional
    public BookingResponse cancel(UUID userId, UUID bookingId) {
        OffsetDateTime now = OffsetDateTime.now();
        Booking booking = load(userId, bookingId);
        if (booking.isCancelled()) {
            throw Problems.conflict("booking_already_cancelled", "That booking has already been cancelled.");
        }
        booking.cancel(now);
        booking.recordEvent(BookingEventType.CANCELLED, "Cancelled and released seats " + labelsOf(booking) + ".",
                userId, now);
        // Load-bearing, and it must come before the delete. deleteByBookingId
        // clears the persistence context, which would discard the cancellation
        // and the event above without raising anything at all.
        bookings.flush();
        // Built while the booking is still managed, for the same reason.
        BookingResponse response = BookingResponse.from(booking);
        claims.deleteByBookingId(booking.getId());
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
