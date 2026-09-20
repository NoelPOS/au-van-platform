package com.auvan.api.inventory.service;

import com.auvan.api.inventory.dto.CreateRouteRequest;
import com.auvan.api.inventory.dto.RouteResponse;
import com.auvan.api.inventory.dto.UpdateRouteRequest;
import com.auvan.api.inventory.entity.RouteStatus;
import com.auvan.api.inventory.entity.VanRoute;
import com.auvan.api.inventory.repository.VanRouteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class RouteService {
    private final VanRouteRepository routes;

    public RouteService(VanRouteRepository routes) {
        this.routes = routes;
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> list() {
        return routes.findAll().stream().map(RouteResponse::from).toList();
    }

    @Transactional
    public RouteResponse create(CreateRouteRequest request) {
        validateEndpoints(request.origin(), request.destination());
        return RouteResponse.from(routes.save(new VanRoute(
                request.origin().trim(), request.destination().trim(), request.fare(), request.durationMinutes())));
    }

    @Transactional
    public RouteResponse update(UUID id, UpdateRouteRequest request) {
        VanRoute route = find(id);
        String origin = request.origin() == null ? route.getOrigin() : request.origin().trim();
        String destination = request.destination() == null ? route.getDestination() : request.destination().trim();
        validateEndpoints(origin, destination);
        route.update(origin, destination,
                request.fare() == null ? route.getFare() : request.fare(),
                request.durationMinutes() == null ? route.getDurationMinutes() : request.durationMinutes(),
                request.status() == null ? route.getStatus() : request.status());
        return RouteResponse.from(route);
    }

    private VanRoute find(UUID id) {
        return routes.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route not found."));
    }

    private void validateEndpoints(String origin, String destination) {
        if (origin == null || origin.isBlank() || destination == null || destination.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route origin and destination are required.");
        }
        if (origin.trim().equalsIgnoreCase(destination.trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route origin and destination must differ.");
        }
    }
}
