package com.auvan.api.inventory.service;

import com.auvan.api.inventory.dto.CreateTripRequest;
import com.auvan.api.inventory.dto.TripResponse;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.VanRoute;
import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.inventory.entity.VehicleStatus;
import com.auvan.api.inventory.entity.RouteStatus;
import com.auvan.api.inventory.repository.TripRepository;
import com.auvan.api.inventory.repository.VanRouteRepository;
import com.auvan.api.inventory.repository.VehicleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class TripService {
    private final TripRepository trips;
    private final VanRouteRepository routes;
    private final VehicleRepository vehicles;

    public TripService(TripRepository trips, VanRouteRepository routes, VehicleRepository vehicles) {
        this.trips = trips;
        this.routes = routes;
        this.vehicles = vehicles;
    }

    @Transactional(readOnly = true)
    public List<TripResponse> list() {
        return trips.findAll().stream().map(TripResponse::from).toList();
    }

    @Transactional
    public TripResponse create(CreateTripRequest request) {
        Vehicle vehicle = findVehicle(request.vehicleId());
        assertVehicleAvailable(vehicle);
        if (trips.existsByVehicleIdAndDepartureAt(vehicle.getId(), request.departureAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This vehicle already has a trip at that departure time.");
        }
        try {
            return TripResponse.from(trips.save(new Trip(findRoute(request.routeId()), vehicle, request.departureAt())));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This vehicle already has a trip at that departure time.", exception);
        }
    }

    private VanRoute findRoute(UUID id) {
        VanRoute route = routes.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route not found."));
        if (route.getStatus() != RouteStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trips require an active route.");
        }
        return route;
    }

    private Vehicle findVehicle(UUID id) {
        return vehicles.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vehicle not found."));
    }

    private void assertVehicleAvailable(Vehicle vehicle) {
        if (vehicle.getStatus() != VehicleStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trips require an active vehicle.");
        }
    }
}
