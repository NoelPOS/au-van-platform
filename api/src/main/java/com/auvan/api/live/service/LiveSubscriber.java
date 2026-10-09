package com.auvan.api.live.service;

import com.auvan.api.live.dto.LiveSignal;

import java.util.UUID;

public record LiveSubscriber(UUID userId, boolean admin) {
    public boolean receives(LiveSignal signal) {
        return signal.ownerId() == null || admin || signal.ownerId().equals(userId);
    }
}
