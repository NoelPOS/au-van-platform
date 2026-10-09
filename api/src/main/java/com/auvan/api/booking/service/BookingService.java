package com.auvan.api.booking.service;

import com.auvan.api.booking.config.BookingProperties;
import com.auvan.api.booking.dto.BookingResponse;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.BookingSeat;
import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.live.dto.LiveSignal;
import com.auvan.api.live.service.LiveSignalPublisher;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.service.OutboxRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class BookingService {
    static final String CREATE_ENDPOINT = "POST /api/v1/bookings";

    private final BookingWriter writer;
    private final IdempotencyService idempotency;
    private final BookingRepository bookings;
    private final SeatClaimRepository claims;
    private final OutboxRecorder outbox;
    private final DepartureReminderService reminders;
    private final BookingProperties properties;
    private final LiveSignalPublisher live;

    public BookingService(BookingWriter writer, IdempotencyService idempotency, BookingRepository bookings,
                          SeatClaimRepository claims, OutboxRecorder outbox, DepartureReminderService reminders,
                          BookingProperties properties, LiveSignalPublisher live) {
        this.writer = writer;
        this.idempotency = idempotency;
        this.bookings = bookings;
        this.claims = claims;
        this.outbox = outbox;
        this.reminders = reminders;
        this.properties = properties;
        this.live = live;
    }

    // Not @Transactional: a failed write is rollback-only, so the replay needs its own transaction.
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
            return idempotency.find(userId, CREATE_ENDPOINT, key, requestHash).orElseThrow(() -> failure);
        }
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> list(UUID userId) {
        return bookings.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(booking -> BookingResponse.from(booking, properties))
                .toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID userId, UUID bookingId) {
        return BookingResponse.from(load(userId, bookingId), properties);
    }

    @Transactional
    public BookingResponse cancel(UUID userId, UUID bookingId) {
        OffsetDateTime now = OffsetDateTime.now();
        Booking booking = bookings.lockByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> Problems.notFound("booking_not_found", "Booking not found."));
        if (booking.isCancelled()) {
            throw Problems.conflict("booking_already_cancelled", "That booking has already been cancelled.");
        }
        if (!now.isBefore(properties.cancellableUntil(booking.getTrip().getDepartureAt()))) {
            throw Problems.conflict("cancellation_closed", "This booking can no longer be cancelled. Bookings can be "
                    + "cancelled until " + properties.cancellationClosesBeforeDeparture().toMinutes()
                    + " minutes before departure.");
        }
        booking.cancel(now);
        String detail = "Cancelled and released seats " + labelsOf(booking) + "."
                + BookingNotifications.refundNote(booking);
        booking.recordEvent(BookingEventType.CANCELLED, detail, userId, now);
        // Flush and build the response first: deleteByBookingId clears the context.
        outbox.record(OutboxEventType.BOOKING_CANCELLED, booking.getId(), booking.getUserId(),
                BookingNotifications.of(booking, detail), now);
        bookings.flush();
        BookingResponse response = BookingResponse.from(booking, properties);
        live.publish(LiveSignal.trip(booking.getTrip().getId()));
        claims.deleteByBookingId(booking.getId());
        reminders.cancel(bookingId, now);
        return response;
    }

    private Booking load(UUID userId, UUID bookingId) {
        return bookings.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> Problems.notFound("booking_not_found", "Booking not found."));
    }

    private static String labelsOf(Booking booking) {
        return booking.getSeats().stream().map(BookingSeat::getTripSeat).map(TripSeat::getLabel).sorted()
                .collect(Collectors.joining(", "));
    }
}
