package com.auvan.api.inventory.dto;

import com.auvan.api.inventory.entity.RouteStatus;
import com.auvan.api.inventory.entity.VanRoute;

import java.math.BigDecimal;
import java.util.UUID;

public record RouteResponse(
        UUID id,
        String origin,
        String destination,
        BigDecimal fare,
        int durationMinutes,
        RouteStatus status) {
    public static RouteResponse from(VanRoute route) {
        return new RouteResponse(route.getId(), route.getOrigin(), route.getDestination(), route.getFare(),
                route.getDurationMinutes(), route.getStatus());
    }
}
