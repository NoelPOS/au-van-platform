package com.auvan.api.outbox;

import com.auvan.api.notification.client.LinePushMessage;
import com.auvan.api.outbox.entity.OutboxStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxRetryIntegrationTests extends OutboxTestSupport {
    @Test
    void aSendThatThrowsLeavesTheRowPendingWithOneSpentAttemptAndABackedOffDeadline() {
        UUID eventId = record("AUV-250101-FAILS");
        sender.failNext(1);

        assertThat(dispatcher.dispatchBatch()).isZero();

        assertThat(events.findById(eventId).orElseThrow()).satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getAttempts()).isOne();
            assertThat(event.getLastError()).contains("LINE did not accept the push.");
            assertThat(event.getProcessedAt()).isNull();
            assertThat(event.getNextAttemptAt()).isAfter(OffsetDateTime.now().plusSeconds(20));
        });
        assertThat(dispatcher.dispatchBatch()).isZero();
    }

    @Test
    void aRetryCarriesTheSameRetryKeyAsTheSendThatFailed() {
        UUID eventId = record("AUV-250101-RETRYKEY");
        sender.failNext(1);

        dispatcher.dispatchBatch();
        dueAt(eventId, OffsetDateTime.now().minusSeconds(1));
        assertThat(dispatcher.dispatchBatch()).isOne();

        assertThat(sender.messages()).hasSize(2);
        assertThat(sender.messages()).extracting(LinePushMessage::retryKey).containsOnly(eventId.toString());
        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    @Test
    void aRowThatExhaustsItsAttemptsGoesDeadAndStopsBeingRetried() {
        UUID eventId = record("AUV-250101-DOOMED");
        sender.failNext(10);

        for (int attempt = 1; attempt <= 5; attempt++) {
            dueAt(eventId, OffsetDateTime.now().minusSeconds(1));
            dispatcher.dispatchBatch();
        }

        assertThat(events.findById(eventId).orElseThrow()).satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD);
            assertThat(event.getAttempts()).isEqualTo(5);
            assertThat(event.getLastError()).contains("LINE did not accept the push.");
            assertThat(event.getProcessedAt()).isNotNull();
        });
        assertThat(sender.messages()).hasSize(5);

        dueAt(eventId, OffsetDateTime.now().minusSeconds(1));
        assertThat(dispatcher.dispatchBatch()).isZero();
        assertThat(sender.messages()).hasSize(5);
    }
}
