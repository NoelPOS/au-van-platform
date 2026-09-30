package com.auvan.api.notification.client;

public interface LineMessageSender {
    void send(LinePushMessage message);
}
