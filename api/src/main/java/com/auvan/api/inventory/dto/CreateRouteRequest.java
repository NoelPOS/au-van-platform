package com.auvan.api.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateRouteRequest(
        @NotBlank String origin,
        @NotBlank String destination,
        @DecimalMin("0.00") BigDecimal fare,
        @Positive int durationMinutes) { }
