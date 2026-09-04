package com.auvan.api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LineExchangeRequest(@NotBlank String idToken) { }
