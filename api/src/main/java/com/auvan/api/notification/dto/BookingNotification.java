package com.auvan.api.notification.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

// Every field after detail is null in a row written before the card fields existed.
public record BookingNotification(String reference, String detail, String origin, String destination,
                                  OffsetDateTime departureAt, List<String> seats, BigDecimal fare,
                                  OffsetDateTime paymentDeadlineAt) { }
