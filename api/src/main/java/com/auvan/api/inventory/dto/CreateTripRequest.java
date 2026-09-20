package com.auvan.api.inventory.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateTripRequest(
        @NotNull UUID routeId,
        @NotNull UUID vehicleId,
        @NotNull @Future OffsetDateTime departureAt) { }
