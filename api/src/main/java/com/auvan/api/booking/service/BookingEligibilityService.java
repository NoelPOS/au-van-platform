package com.auvan.api.booking.service;

import com.auvan.api.booking.dto.BookingEligibilityResponse;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.inventory.entity.Trip;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingEligibilityService {
    private static final List<BookingStatus> UNPAID = List.of(BookingStatus.PENDING_PAYMENT,
            BookingStatus.PAYMENT_REJECTED);

    private final BookingRepository bookings;

    public BookingEligibilityService(BookingRepository bookings) {
        this.bookings = bookings;
    }

    @Transactional(readOnly = true)
    public BookingEligibilityResponse eligibilityOf(UUID userId, OffsetDateTime now) {
        return bookings.findFirstByUserIdAndStatusInOrderByCreatedAtAsc(userId, UNPAID)
                .map(unpaid -> BookingEligibilityResponse.unpaid(unpaid, "You have an unpaid booking, "
                        + unpaid.getReference() + ". Pay for it or cancel it before booking another seat."))
                .orElseGet(BookingEligibilityResponse::eligible);
    }

    public void assertMayBookOn(UUID userId, Trip trip, OffsetDateTime now) {
        assertNotBookedOn(userId, trip);
        BookingEligibilityResponse eligibility = eligibilityOf(userId, now);
        if (!eligibility.canBook()) {
            throw refusal(eligibility);
        }
    }

    public void assertNotBookedOn(UUID userId, Trip trip) {
        if (bookings.existsByUserIdAndTripIdAndStatusNot(userId, trip.getId(), BookingStatus.CANCELLED)) {
            throw Problems.conflict("already_booked_on_trip", "You already have a booking on this trip.");
        }
    }

    private static ResponseStatusException refusal(BookingEligibilityResponse eligibility) {
        return Problems.conflict(eligibility.reason(), eligibility.message(), Map.of(
                "bookingId", eligibility.unpaidBookingId(),
                "bookingReference", eligibility.unpaidBookingReference()));
    }
}
