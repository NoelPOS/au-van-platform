package com.auvan.api.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

// Price and seats come from the hold server-side; never accept them from the client.
public record CreateBookingRequest(
        @NotNull UUID holdId,
        @NotBlank @Size(min = 2, max = 100) String passengerName,
        @NotBlank @Size(min = 9, max = 15) String passengerPhone) { }
