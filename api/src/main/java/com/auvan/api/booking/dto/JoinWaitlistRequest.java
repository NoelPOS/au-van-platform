package com.auvan.api.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

/**
 * {@code seatsWanted} is bounded here only at the bottom. The ceiling is
 * {@code booking.max-seats-per-hold} and is applied in the service, so the
 * waitlist and a direct hold refuse the same number for the same reason and
 * with the same message.
 */
public record JoinWaitlistRequest(
        @NotNull UUID tripId,
        @Positive int seatsWanted) { }
