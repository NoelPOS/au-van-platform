package com.auvan.api.notification.client;

public record LinePushMessage(String to, FlexMessage message, String retryKey) { }
