package com.auvan.api.inventory.controller;

import com.auvan.api.inventory.dto.CreateRouteRequest;
import com.auvan.api.inventory.dto.RouteResponse;
import com.auvan.api.inventory.dto.UpdateRouteRequest;
import com.auvan.api.inventory.service.RouteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/routes")
public class RouteAdminController {
    private final RouteService routeService;

    public RouteAdminController(RouteService routeService) {
        this.routeService = routeService;
    }

    @GetMapping
    public List<RouteResponse> list() {
        return routeService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RouteResponse create(@Valid @RequestBody CreateRouteRequest request) {
        return routeService.create(request);
    }

    @PutMapping("/{id}")
    public RouteResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRouteRequest request) {
        return routeService.update(id, request);
    }
}
