package com.auvan.api.booking.service;

import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.DeadLetterResponse;
import com.auvan.api.booking.dto.TripOperationsResponse;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.exception.Problems;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.repository.TripRepository;
import com.auvan.api.outbox.repository.OutboxEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OperationsViewService {
    private static final int DEAD_LETTER_LIMIT = 200;

    private final TripRepository trips;
    private final BookingRepository bookings;
    private final WaitlistEntryRepository waitlist;
    private final SeatClaimRepository claims;
    private final AppUserRepository users;
    private final OutboxEventRepository outbox;

    public OperationsViewService(TripRepository trips, BookingRepository bookings, WaitlistEntryRepository waitlist,
                                 SeatClaimRepository claims, AppUserRepository users, OutboxEventRepository outbox) {
        this.trips = trips;
        this.bookings = bookings;
        this.waitlist = waitlist;
        this.claims = claims;
        this.users = users;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public TripOperationsResponse trip(UUID tripId) {
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> Problems.notFound("trip_not_found", "Trip not found."));
        OffsetDateTime now = OffsetDateTime.now();
        return new TripOperationsResponse(
                trip.getId(),
                trip.getRoute().getOrigin(),
                trip.getRoute().getDestination(),
                trip.getDepartureAt(),
                trip.getStatus(),
                trip.getSeats().size(),
                claimedSeatsOf(trip, now),
                bookingsByStatusOf(trip),
                queueOf(trip));
    }

    @Transactional(readOnly = true)
    public List<DeadLetterResponse> deadLetters() {
        return outbox.findDead(PageRequest.of(0, DEAD_LETTER_LIMIT)).stream()
                .map(DeadLetterResponse::from)
                .toList();
    }

    private int claimedSeatsOf(Trip trip, OffsetDateTime now) {
        return (int) claims.findByTripIdIn(List.of(trip.getId())).stream()
                .filter(claim -> claim.blocksSeatAt(now))
                .count();
    }

    private List<TripOperationsResponse.BookingStatusCount> bookingsByStatusOf(Trip trip) {
        Map<BookingStatus, Long> counted = bookings.findByTripId(trip.getId()).stream()
                .collect(Collectors.groupingBy(Booking::getStatus, Collectors.counting()));
        return Arrays.stream(BookingStatus.values())
                .map(status -> new TripOperationsResponse.BookingStatusCount(status,
                        counted.getOrDefault(status, 0L)))
                .toList();
    }

    private List<TripOperationsResponse.WaitlistPlace> queueOf(Trip trip) {
        List<WaitlistEntry> queue = waitlist.findByTripIdOrderByJoinedAt(trip.getId());
        Map<UUID, String> names = displayNamesOf(queue);
        List<TripOperationsResponse.WaitlistPlace> places = new ArrayList<>();
        int position = 0;
        for (WaitlistEntry entry : queue) {
            Integer place = null;
            if (entry.isQueued()) {
                position++;
                place = position;
            }
            places.add(new TripOperationsResponse.WaitlistPlace(entry.getId(), entry.getUserId(),
                    names.get(entry.getUserId()), entry.getSeatsWanted(), entry.getStatus(), place,
                    entry.getJoinedAt(), entry.getPromotionHoldId(), entry.getPromotionExpiresAt()));
        }
        return places;
    }

    private Map<UUID, String> displayNamesOf(List<WaitlistEntry> queue) {
        List<UUID> userIds = queue.stream().map(WaitlistEntry::getUserId).distinct().toList();
        return users.findAllById(userIds).stream()
                .filter(user -> user.getDisplayName() != null)
                .collect(Collectors.toMap(AppUser::getId, AppUser::getDisplayName, (first, second) -> first));
    }
}
