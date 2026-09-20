package com.auvan.api.inventory.dto;

import com.auvan.api.inventory.entity.TripStatus;
import jakarta.validation.constraints.Future;

import java.time.OffsetDateTime;

public record UpdateTripRequest(@Future OffsetDateTime departureAt, TripStatus status) { }
