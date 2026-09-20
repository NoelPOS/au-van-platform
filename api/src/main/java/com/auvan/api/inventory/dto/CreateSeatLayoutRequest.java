package com.auvan.api.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateSeatLayoutRequest(
        @NotBlank String name,
        @NotEmpty List<@Valid SeatInput> seats) { }
