package com.auvan.api.booking.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record JoinWaitlistRequest(
        @NotNull UUID tripId,
        @Positive int seatsWanted) { }
