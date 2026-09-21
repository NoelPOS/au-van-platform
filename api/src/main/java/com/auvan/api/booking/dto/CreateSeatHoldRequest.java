package com.auvan.api.booking.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateSeatHoldRequest(
        @NotNull UUID tripId,
        @NotEmpty List<@NotNull UUID> seatIds) { }
