package com.auvan.api.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record SeatInput(
        @NotBlank String label,
        @Positive int rowNumber,
        @Positive int columnNumber) { }
