package com.auvan.api.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record SchedulePlanRequest(
        @NotEmpty(message = "Choose at least one day.")
        @Size(max = 62, message = "Plan at most 62 days at once.") List<@NotNull LocalDate> dates,
        @NotEmpty(message = "Add at least one departure.")
        @Size(max = 60, message = "Plan at most 60 departures a day.")
        List<@Valid @NotNull DepartureLine> departures,
        String planHash) { }
