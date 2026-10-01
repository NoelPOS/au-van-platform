package com.auvan.api.notification.client;

import java.util.Map;

public record FlexMessage(String type, String altText, Map<String, Object> contents) {
    public FlexMessage(String altText, Map<String, Object> contents) {
        this("flex", altText, contents);
    }
}
