package com.auvan.api.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelTripRequest(@NotBlank @Size(max = 300) String reason) { }
