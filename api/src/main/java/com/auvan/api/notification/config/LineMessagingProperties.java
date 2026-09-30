package com.auvan.api.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.line")
public record LineMessagingProperties(String channelAccessToken, String apiBaseUrl, boolean enabled) {
    public boolean hasChannelAccessToken() {
        return channelAccessToken != null && !channelAccessToken.isBlank();
    }
}
