package com.auvan.api.live.dto;

import java.util.UUID;

public record LiveSignal(String kind, UUID id, UUID ownerId) {
    public static LiveSignal booking(UUID id, UUID ownerId) {
        return new LiveSignal("booking", id, ownerId);
    }

    public static LiveSignal trip(UUID tripId) {
        return new LiveSignal("trip", tripId, null);
    }
}
