package com.auvan.api.booking.repository;

import com.auvan.api.booking.entity.SeatClaim;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
