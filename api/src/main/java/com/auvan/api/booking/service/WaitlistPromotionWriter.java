package com.auvan.api.booking.service;

import com.auvan.api.booking.config.BookingProperties;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.inventory.entity.TripStatus;
import com.auvan.api.notification.dto.WaitlistNotification;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.service.OutboxRecorder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class WaitlistPromotionWriter {
    private final WaitlistEntryRepository entries;
    private final SeatClaimRepository claims;
    private final SeatAvailabilityService availability;
    private final OutboxRecorder outbox;
    private final BookingProperties properties;

    public WaitlistPromotionWriter(WaitlistEntryRepository entries, SeatClaimRepository claims,
                                   SeatAvailabilityService availability, OutboxRecorder outbox,
                                   BookingProperties properties) {
        this.entries = entries;
        this.claims = claims;
        this.availability = availability;
        this.outbox = outbox;
        this.properties = properties;
    }

    @Transactional
    public boolean promote(UUID entryId, OffsetDateTime now) {
        WaitlistEntry entry = entries.lockById(entryId).orElse(null);
        if (entry == null || !entry.isWaiting()) {
            return false;
        }
        Trip trip = entry.getTrip();
        OffsetDateTime expiresAt = properties.promotionDeadlineFor(trip.getDepartureAt(), now);
        if (trip.getStatus() != TripStatus.ACTIVE || !expiresAt.isAfter(now)
                || !now.isBefore(properties.bookingClosesAt(trip.getDepartureAt()))) {
            return false;
        }

        List<TripSeat> free = availability.freeSeatsOf(trip, now);
        if (free.size() < entry.getSeatsWanted()) {
            return false;
        }
        List<TripSeat> taking = free.subList(0, entry.getSeatsWanted());

        UUID userId = entry.getUserId();
        UUID holdId = UUID.randomUUID();
        entry.promote(holdId, expiresAt, now);
        outbox.record(OutboxEventType.WAITLIST_PROMOTED, entry.getId(), userId,
                notificationOf(trip, "Take the seats by " + BookingNotifications.moment(expiresAt) + ".",
                        taking.stream().map(TripSeat::getLabel).sorted().toList(),
                        trip.getFare().multiply(BigDecimal.valueOf(taking.size())), expiresAt),
                now);
        // Flush before the reclaim: deleteByIdIn clears the context and discards these changes.
        entries.flush();
        reclaim(taking, now);
        insert(taking, userId, holdId, expiresAt);
        return true;
    }

    @Transactional
    public boolean resolve(UUID entryId, OffsetDateTime now) {
        WaitlistEntry entry = entries.lockById(entryId).orElse(null);
        if (entry == null || !entry.hasLapsedAt(now)) {
            return false;
        }
        if (claims.findByHoldId(entry.getPromotionHoldId()).stream().anyMatch(SeatClaim::isBooked)) {
            entry.fulfil(now);
            return true;
        }
        entry.expire(now);
        outbox.record(OutboxEventType.WAITLIST_PROMOTION_EXPIRED, entry.getId(), entry.getUserId(),
                notificationOf(entry.getTrip(),
                        "The offer ran out on " + BookingNotifications.moment(entry.getPromotionExpiresAt()) + ".",
                        null, null, entry.getPromotionExpiresAt()), now);
        return true;
    }

    // Bulk delete runs at once; a flush would order the inserts first and collide.
    private void reclaim(List<TripSeat> taking, OffsetDateTime now) {
        List<UUID> lapsed = claims.findBySeatIdIn(taking.stream().map(TripSeat::getId).toList()).stream()
                .filter(claim -> !claim.blocksSeatAt(now))
                .map(SeatClaim::getId)
                .toList();
        if (!lapsed.isEmpty()) {
            claims.deleteByIdIn(lapsed);
        }
    }

    private void insert(List<TripSeat> taking, UUID userId, UUID holdId, OffsetDateTime expiresAt) {
        try {
            claims.saveAll(taking.stream().map(seat -> new SeatClaim(seat, userId, holdId, expiresAt)).toList());
            // Flush inside the try: @UuidGenerator defers the insert past this catch.
            claims.flush();
        } catch (DataIntegrityViolationException raced) {
            throw new PromotionRaceLostException(raced);
        }
    }

    private static WaitlistNotification notificationOf(Trip trip, String detail, List<String> seats,
                                                       BigDecimal fare, OffsetDateTime offerExpiresAt) {
        String summary = trip.getRoute().getOrigin() + " to " + trip.getRoute().getDestination()
                + ", departing " + BookingNotifications.moment(trip.getDepartureAt()) + ".";
        return new WaitlistNotification(summary, detail, trip.getRoute().getOrigin(),
                trip.getRoute().getDestination(), trip.getDepartureAt(), seats, fare, offerExpiresAt);
    }
}
