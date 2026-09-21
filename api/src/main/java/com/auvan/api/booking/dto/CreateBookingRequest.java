package com.auvan.api.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Everything a confirmation may say. The trip, the seats, and the price are all
 * derived from the hold on the server: a client-supplied price is never trusted,
 * and a client-supplied seat list could disagree with the seats it holds.
 */
public record CreateBookingRequest(
        @NotNull UUID holdId,
        @NotBlank @Size(min = 2, max = 100) String passengerName,
        @NotBlank @Size(min = 9, max = 15) String passengerPhone) { }
