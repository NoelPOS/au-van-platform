package com.auvan.api.inventory.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record SchedulePreviewResponse(String planHash, List<PlannedDeparture> departures) {
    public enum Outcome { CREATE, CLASH, PAST, UNAVAILABLE }

    public record PlannedDeparture(
            LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime time,
            UUID routeId,
            UUID vehicleId,
            Outcome outcome,
            String reason) { }
}
