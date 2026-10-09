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

    // Serialises confirmations of one hold, which the unique constraint cannot see. No fetch
    // join: PostgreSQL refuses FOR UPDATE on the nullable side of an outer join.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select claim from SeatClaim claim where claim.holdId = :holdId")
    List<SeatClaim> lockByHoldId(@Param("holdId") UUID holdId);

    List<SeatClaim> findByBookingId(UUID bookingId);

    // Bulk rather than deleteAll, which throws StaleStateException when two reclaim one row.
    // The booking_id is null guard stops it freeing a seat sold since the read; never widen it.
    @Modifying(clearAutomatically = true)
    @Query("delete from SeatClaim claim where claim.id in :ids and claim.bookingId is null")
    void deleteByIdIn(@Param("ids") Collection<UUID> ids);

    // clearAutomatically discards unflushed changes: flush before calling this.
    @Modifying(clearAutomatically = true)
    @Query("delete from SeatClaim claim where claim.bookingId = :bookingId")
    void deleteByBookingId(@Param("bookingId") UUID bookingId);

    // Booked claims only: an unbooked hold may be locked by a BookingWriter waiting on this trip's
    // lock, and deleting it would deadlock. clearAutomatically: flush before calling this.
    @Modifying(clearAutomatically = true)
    @Query("""
            delete from SeatClaim claim
            where claim.bookingId in (select booking.id from Booking booking where booking.trip.id = :tripId)
            """)
    void deleteBookedOnTrip(@Param("tripId") UUID tripId);
}
