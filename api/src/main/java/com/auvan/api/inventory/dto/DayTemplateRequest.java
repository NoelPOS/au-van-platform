package com.auvan.api.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DayTemplateRequest(
        @NotBlank(message = "Give the template a name.")
        @Size(max = 60, message = "Keep the name to 60 characters.") String name,
        @NotEmpty(message = "Add at least one departure.")
        @Size(max = 60, message = "A template holds at most 60 departures.")
        List<@Valid @NotNull DepartureLine> departures) { }
