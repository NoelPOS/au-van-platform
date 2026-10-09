package com.auvan.api.live.service;

import com.auvan.api.live.dto.LiveSignal;

public interface LiveSignalPublisher {
    void publish(LiveSignal signal);
}
