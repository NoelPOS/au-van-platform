package com.auvan.api.inventory.repository;

import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    boolean existsByVehicleIdAndDepartureAt(UUID vehicleId, OffsetDateTime departureAt);
    boolean existsByVehicleIdAndDepartureAtAndIdNot(UUID vehicleId, OffsetDateTime departureAt, UUID id);

    @Query("""
            select trip from Trip trip
            join fetch trip.route
            left join fetch trip.seats
            where trip.status = :status and trip.departureAt > :departingAfter
            order by trip.departureAt
            """)
    List<Trip> findBookable(@Param("status") TripStatus status, @Param("departingAfter") OffsetDateTime departingAfter);
}
