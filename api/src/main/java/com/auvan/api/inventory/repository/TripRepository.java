package com.auvan.api.inventory.repository;

import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    boolean existsByVehicleIdAndDepartureAt(UUID vehicleId, OffsetDateTime departureAt);
    boolean existsByVehicleIdAndDepartureAtAndIdNot(UUID vehicleId, OffsetDateTime departureAt, UUID id);

    // No fetch join: PostgreSQL refuses FOR UPDATE on the nullable side of an outer join.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select trip from Trip trip where trip.id = :id")
    Optional<Trip> lockById(@Param("id") UUID id);

    @Query("""
            select trip from Trip trip
            join fetch trip.route
            where trip.vehicle.id in :vehicleIds and trip.departureAt >= :from and trip.departureAt < :until
            """)
    List<Trip> findForVehiclesBetween(@Param("vehicleIds") Collection<UUID> vehicleIds,
                                      @Param("from") OffsetDateTime from, @Param("until") OffsetDateTime until);

    long countByDepartureAtGreaterThanAndDepartureAtLessThan(OffsetDateTime after, OffsetDateTime until);

    @Query("""
            select trip from Trip trip
            where trip.departureAt > :after and trip.departureAt < :until
              and not exists (select booking from Booking booking where booking.trip = trip)
              and not exists (select claim from SeatClaim claim where claim.tripSeat.trip = trip)
              and not exists (select entry from WaitlistEntry entry where entry.trip = trip)
            """)
    List<Trip> findUnclaimedBetween(@Param("after") OffsetDateTime after, @Param("until") OffsetDateTime until);

    @Query("""
            select trip from Trip trip
            join fetch trip.route
            left join fetch trip.seats
            where trip.status = :status and trip.departureAt > :departingAfter
            order by trip.departureAt
            """)
    List<Trip> findBookable(@Param("status") TripStatus status, @Param("departingAfter") OffsetDateTime departingAfter);
}
