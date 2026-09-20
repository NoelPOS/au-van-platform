package com.auvan.api.inventory.controller;

import com.auvan.api.inventory.dto.CreateVehicleRequest;
import com.auvan.api.inventory.dto.UpdateVehicleRequest;
import com.auvan.api.inventory.dto.VehicleResponse;
import com.auvan.api.inventory.service.VehicleService;
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
@RequestMapping("/api/v1/admin/vehicles")
public class VehicleAdminController {
    private final VehicleService vehicleService;

    public VehicleAdminController(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    @GetMapping
    public List<VehicleResponse> list() {
        return vehicleService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleResponse create(@Valid @RequestBody CreateVehicleRequest request) {
        return vehicleService.create(request);
    }

    @PutMapping("/{id}")
    public VehicleResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateVehicleRequest request) {
        return vehicleService.update(id, request);
    }
}
