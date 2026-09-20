package com.auvan.api.inventory.dto;

import com.auvan.api.inventory.entity.VehicleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateVehicleRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull UUID seatLayoutId,
        @NotNull VehicleStatus status) { }
