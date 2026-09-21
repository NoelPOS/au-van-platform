package com.auvan.api.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "booking")
public record BookingProperties(Duration holdTtl, int maxSeatsPerHold) { }
