package com.auvan.api.booking.repository;

import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.RefundStatus;
import com.auvan.api.inventory.entity.TripStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Booking> findByIdAndUserId(UUID id, UUID userId);

    List<Booking> findByTripId(UUID tripId);

    List<Booking> findByRefundStatusOrderByUpdatedAtAsc(RefundStatus refundStatus);

    // Ids, not entities: a booking loaded here is handed back stale by the later lockById.
    @Query("""
            select booking.id from Booking booking
            where booking.trip.id = :tripId
              and booking.status <> com.auvan.api.booking.entity.BookingStatus.CANCELLED
            order by booking.createdAt asc
            """)
    List<UUID> findActiveIdsByTripId(@Param("tripId") UUID tripId);

    Optional<Booking> findFirstByUserIdAndStatusInOrderByCreatedAtAsc(UUID userId, Collection<BookingStatus> statuses);

    boolean existsByUserIdAndTripIdAndStatusNot(UUID userId, UUID tripId, BookingStatus status);

    @Query("""
            select event.createdAt from BookingEvent event
            where event.booking.userId = :userId
              and event.eventType = com.auvan.api.booking.entity.BookingEventType.EXPIRED
              and event.createdAt > :since
            order by event.createdAt desc
            """)
    List<OffsetDateTime> findExpiryTimesSince(@Param("userId") UUID userId, @Param("since") OffsetDateTime since);

    // No fetch join: PostgreSQL refuses FOR UPDATE on the nullable side of an outer join.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.id = :id and booking.userId = :userId")
    Optional<Booking> lockByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    // Not owner-scoped: call only after the caller has been authorised.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select booking from Booking booking where booking.id = :id")
    Optional<Booking> lockById(@Param("id") UUID id);

    // Ids, not entities: a booking loaded here is handed back stale by the later lockById.
    @Query("""
            select booking.id from Booking booking
            where booking.status in (com.auvan.api.booking.entity.BookingStatus.PENDING_PAYMENT,
                                     com.auvan.api.booking.entity.BookingStatus.PAYMENT_REJECTED)
              and booking.paymentDeadlineAt <= :now
            order by booking.paymentDeadlineAt asc
            """)
    List<UUID> findExpirable(@Param("now") OffsetDateTime now, Pageable pageable);

    record TripFigures(UUID routeId, String origin, String destination, OffsetDateTime departureAt,
                       TripStatus status, long seats, long seatsSold, long bookings, BigDecimal revenue,
                       BigDecimal awaitingPayment, BigDecimal refundsDue, BigDecimal refunded) { }

    @Query("""
            select new com.auvan.api.booking.repository.BookingRepository$TripFigures(
                route.id, route.origin, route.destination, trip.departureAt, trip.status,
                (select count(seat) from TripSeat seat where seat.trip = trip),
                (select count(sold) from BookingSeat sold
                 where sold.booking.trip = trip and sold.booking.status = CONFIRMED),
                (select count(booking) from Booking booking
                 where booking.trip = trip and booking.status <> CANCELLED),
                (select coalesce(sum(booking.totalFare), 0) from Booking booking
                 where booking.trip = trip and booking.status = CONFIRMED),
                (select coalesce(sum(booking.totalFare), 0) from Booking booking
                 where booking.trip = trip
                   and booking.status in (PENDING_PAYMENT, PAYMENT_UNDER_REVIEW, PAYMENT_REJECTED)),
                (select coalesce(sum(booking.totalFare), 0) from Booking booking
                 where booking.trip = trip and booking.refundStatus = DUE),
                (select coalesce(sum(booking.totalFare), 0) from Booking booking
                 where booking.trip = trip and booking.refundStatus = REFUNDED))
            from Trip trip join trip.route route
            where trip.departureAt >= :from and trip.departureAt < :until
            """)
    List<TripFigures> findTripFiguresBetween(@Param("from") OffsetDateTime from,
                                             @Param("until") OffsetDateTime until);
}
