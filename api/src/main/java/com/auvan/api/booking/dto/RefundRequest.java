package com.auvan.api.booking.dto;

import jakarta.validation.constraints.Size;

public record RefundRequest(@Size(max = 500) String note) { }
