package com.auvan.api.inventory.dto;

import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.inventory.entity.VehicleStatus;

import java.util.UUID;

public record VehicleResponse(UUID id, String code, String name, UUID seatLayoutId, VehicleStatus status) {
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(vehicle.getId(), vehicle.getCode(), vehicle.getName(),
                vehicle.getSeatLayout().getId(), vehicle.getStatus());
    }
}
