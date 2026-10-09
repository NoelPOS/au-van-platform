package com.auvan.api.inventory.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ClearDayRequest(@NotNull(message = "Choose a day to clear.") LocalDate date) { }
