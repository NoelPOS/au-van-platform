package com.auvan.api.inventory.dto;

import com.auvan.api.inventory.entity.RouteStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record UpdateRouteRequest(
        String origin,
        String destination,
        @DecimalMin("0.00") BigDecimal fare,
        @Positive Integer durationMinutes,
        RouteStatus status) { }
