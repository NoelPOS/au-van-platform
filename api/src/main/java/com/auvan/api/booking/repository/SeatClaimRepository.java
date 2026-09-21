package com.auvan.api.booking.repository;

import com.auvan.api.booking.entity.SeatClaim;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SeatClaimRepository extends JpaRepository<SeatClaim, UUID> {
    /** Every claim on the given trips, with its seat, so a seat map costs one query. */
    @Query("""
            select claim from SeatClaim claim
            join fetch claim.tripSeat seat
            join fetch seat.trip
            where seat.trip.id in :tripIds
            """)
    List<SeatClaim> findByTripIdIn(@Param("tripIds") Collection<UUID> tripIds);

    @Query("select claim from SeatClaim claim where claim.tripSeat.id in :seatIds")
    List<SeatClaim> findBySeatIdIn(@Param("seatIds") Collection<UUID> seatIds);

    @Query("""
            select claim from SeatClaim claim
            where claim.tripSeat.trip.id = :tripId
              and claim.userId = :userId
              and claim.bookingId is null
            """)
    List<SeatClaim> findHoldsOnTripBy(@Param("tripId") UUID tripId, @Param("userId") UUID userId);

    List<SeatClaim> findByHoldId(UUID holdId);

    /**
     * The hold's claims, with their rows locked until the transaction ends.
     *
     * <p>This is what stops one hold being confirmed twice, which the unique
     * constraint cannot see: both confirmations would {@code UPDATE} rows that
     * already exist, so neither violates {@code seat_claims_trip_seat_unique}
     * and two bookings end up sharing a seat. Every decision on the confirmation
     * path must be made from the rows this query returned — a reading taken
     * before the lock is stale and defeats it.
     *
     * <p>No fetch join: PostgreSQL refuses {@code FOR UPDATE} on the nullable
     * side of an outer join.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select claim from SeatClaim claim where claim.holdId = :holdId")
    List<SeatClaim> lockByHoldId(@Param("holdId") UUID holdId);

    List<SeatClaim> findByBookingId(UUID bookingId);

    /**
     * Deletes reclaimed and replaced claims in one statement. This is deliberately
     * a bulk delete rather than {@code deleteAll}: it runs immediately, and it
     * skips the row-count check that would turn two students reclaiming the same
     * expired claim into a {@code StaleStateException} for the loser.
     *
     * <p>{@code booking_id is null} is a correctness guard, not an optimisation.
     * Every caller selects its rows from a read taken earlier in the transaction,
     * and a confirmation can attach a booking to one of those rows in between —
     * the row lock does not help, because the reclaimer simply waits and then
     * deletes a row that has since been sold. Matching on the current value of
     * {@code booking_id} is what makes the delete refuse to free a booked seat.
     */
    // clearAutomatically so the deleted rows do not linger in the persistence
    // context; harmless today, a trap as soon as a caller re-reads after a delete.
    @Modifying(clearAutomatically = true)
    @Query("delete from SeatClaim claim where claim.id in :ids and claim.bookingId is null")
    void deleteByIdIn(@Param("ids") Collection<UUID> ids);

    /**
     * Frees a cancelled booking's seats. Scoped by booking rather than by claim
     * id because {@link #deleteByIdIn} deliberately refuses booked claims, which
     * is exactly what these are.
     *
     * <p>{@code clearAutomatically} empties the persistence context, so anything
     * the caller has changed but not yet flushed is discarded here without an
     * error. Flush before calling this.
     */
    @Modifying(clearAutomatically = true)
    @Query("delete from SeatClaim claim where claim.bookingId = :bookingId")
    void deleteByBookingId(@Param("bookingId") UUID bookingId);
}
