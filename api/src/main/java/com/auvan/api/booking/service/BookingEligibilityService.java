package com.auvan.api.booking.service;

import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.config.BookingProperties;
import com.auvan.api.booking.dto.BookingEligibilityResponse;
import com.auvan.api.booking.entity.BookingCooldownClear;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.repository.BookingCooldownClearRepository;
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
    private final BookingCooldownClearRepository clears;
    private final AppUserRepository users;
    private final BookingProperties properties;

    public BookingEligibilityService(BookingRepository bookings, BookingCooldownClearRepository clears,
                                     AppUserRepository users, BookingProperties properties) {
        this.bookings = bookings;
        this.clears = clears;
        this.users = users;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public BookingEligibilityResponse eligibilityOf(UUID userId, OffsetDateTime now) {
        var unpaid = bookings.findFirstByUserIdAndStatusInOrderByCreatedAtAsc(userId, UNPAID);
        if (unpaid.isPresent()) {
            return BookingEligibilityResponse.unpaid(unpaid.get(), "You have an unpaid booking, "
                    + unpaid.get().getReference() + ". Pay for it or cancel it before booking another seat.");
        }
        OffsetDateTime retryAt = cooldownEndOf(userId, now);
        if (retryAt != null) {
            return BookingEligibilityResponse.coolingDown(retryAt, "Your recent bookings expired without payment, "
                    + "so you can book again from " + BookingNotifications.moment(retryAt) + ".");
        }
        return BookingEligibilityResponse.eligible();
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

    @Transactional
    public void clearCooldown(UUID adminId, UUID studentId) {
        if (!users.existsById(studentId)) {
            throw Problems.notFound("student_not_found", "Student not found.");
        }
        clears.save(new BookingCooldownClear(studentId, adminId, OffsetDateTime.now()));
    }

    private OffsetDateTime cooldownEndOf(UUID userId, OffsetDateTime now) {
        BookingProperties.Cooldown cooldown = properties.cooldown();
        OffsetDateTime horizon = now.minus(cooldown.lookback()).minus(cooldown.duration());
        OffsetDateTime since = clears.findLatestClearedAt(userId).filter(horizon::isBefore).orElse(horizon);
        List<OffsetDateTime> expiries = bookings.findExpiryTimesSince(userId, since);
        // Newest first, so the first run of expiries inside the lookback is the one that ends last.
        for (int latest = 0; latest + cooldown.expiries() <= expiries.size(); latest++) {
            OffsetDateTime earliest = expiries.get(latest + cooldown.expiries() - 1);
            if (!expiries.get(latest).isAfter(earliest.plus(cooldown.lookback()))) {
                OffsetDateTime endsAt = expiries.get(latest).plus(cooldown.duration());
                return endsAt.isAfter(now) ? endsAt : null;
            }
        }
        return null;
    }

    private static ResponseStatusException refusal(BookingEligibilityResponse eligibility) {
        Map<String, Object> details = eligibility.retryAt() != null
                ? Map.of("retryAt", eligibility.retryAt())
                : Map.of("bookingId", eligibility.unpaidBookingId(),
                        "bookingReference", eligibility.unpaidBookingReference());
        return Problems.conflict(eligibility.reason(), eligibility.message(), details);
    }
}
