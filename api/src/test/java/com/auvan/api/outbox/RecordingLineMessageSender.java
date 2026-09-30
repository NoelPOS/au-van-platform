package com.auvan.api.outbox;

import com.auvan.api.notification.client.LineMessageSender;
import com.auvan.api.notification.client.LinePushMessage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingLineMessageSender implements LineMessageSender {
    private final List<LinePushMessage> messages = new CopyOnWriteArrayList<>();
    private volatile int failuresLeft;

    @Override
    public void send(LinePushMessage message) {
        messages.add(message);
        if (failuresLeft > 0) {
            failuresLeft--;
            throw new IllegalStateException("LINE did not accept the push.");
        }
    }

    public void failNext(int count) {
        failuresLeft = count;
    }

    public List<LinePushMessage> messages() {
        return List.copyOf(messages);
    }

    public void reset() {
        messages.clear();
        failuresLeft = 0;
    }
}
