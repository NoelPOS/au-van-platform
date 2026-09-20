package com.auvan.api.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateVehicleRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull UUID seatLayoutId) { }
