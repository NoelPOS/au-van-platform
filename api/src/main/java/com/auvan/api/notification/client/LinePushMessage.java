package com.auvan.api.notification.client;

public record LinePushMessage(String to, String text, String retryKey) { }
