package com.auvan.api.outbox;

import com.auvan.api.notification.client.LineMessageSender;
import com.auvan.api.notification.client.LinePushMessage;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The port's test implementation, and the reason it is a port at all: the
 * dispatcher can be exercised — and made to fail — with no LINE channel and no
 * credential, exactly as {@code InMemoryPaymentProofStorage} does for object
 * storage.
 *
 * <p>A failing send <strong>records the message first</strong>. That is the
 * case that matters: a send that reached LINE and then failed on the way back
 * is indistinguishable from one that never arrived, so the retry has to carry
 * the same retry key or the student sees the message twice. Recording before
 * throwing is what lets a test see both sends and compare their keys.
 */
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

    /** Makes the next {@code count} sends record their message and then throw. */
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
