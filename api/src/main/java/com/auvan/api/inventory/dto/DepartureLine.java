package com.auvan.api.inventory.dto;

import com.auvan.api.inventory.entity.DayTemplateDeparture;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;
import java.util.UUID;

public record DepartureLine(
        @NotNull @JsonFormat(pattern = "HH:mm") LocalTime time,
        @NotNull UUID routeId,
        @NotNull UUID vehicleId) {
    public static DepartureLine from(DayTemplateDeparture departure) {
        return new DepartureLine(departure.getDepartureTime(), departure.getRoute().getId(),
                departure.getVehicle().getId());
    }
}
