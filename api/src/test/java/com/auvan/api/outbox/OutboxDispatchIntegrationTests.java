package com.auvan.api.outbox;

import com.auvan.api.outbox.entity.OutboxStatus;
import com.auvan.api.outbox.service.OutboxScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxDispatchIntegrationTests extends OutboxTestSupport {
    @Test
    void aDueRowIsSentOnceAndLandsSent() {
        UUID eventId = record("AUV-250101-DISPATCH");

        assertThat(dispatcher.dispatchBatch()).isOne();

        assertThat(sender.messages()).singleElement().satisfies(message -> {
            assertThat(message.to()).isEqualTo("Ustudent-outbox");
            assertThat(message.message().altText()).contains("AUV-250101-DISPATCH");
            assertThat(message.retryKey()).isEqualTo(eventId.toString());
        });
        assertThat(events.findById(eventId).orElseThrow()).satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
            assertThat(event.getAttempts()).isOne();
            assertThat(event.getProcessedAt()).isNotNull();
            assertThat(event.getLastError()).isNull();
        });
    }

    @Test
    void aRowThatIsNotYetDueIsLeftAlone() {
        UUID eventId = record("AUV-250101-FUTURE");
        dueAt(eventId, OffsetDateTime.now().plusHours(1));

        assertThat(dispatcher.dispatchBatch()).isZero();

        assertThat(sender.messages()).isEmpty();
        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void aSentRowIsNeverSentAgainEvenWhenItIsDue() {
        UUID eventId = record("AUV-250101-ALREADY");
        dispatcher.dispatchBatch();
        sender.reset();
        dueAt(eventId, OffsetDateTime.now().minusMinutes(1));

        assertThat(dispatcher.dispatchBatch()).isZero();

        assertThat(sender.messages()).isEmpty();
        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(events.findDispatchable(OffsetDateTime.now(), PageRequest.of(0, 50))).isEmpty();
    }

    @Test
    void theClaimItselfRefusesARowThatHasAlreadyBeenResolved() {
        UUID sentId = record("AUV-250101-CLAIMSENT");
        UUID deadId = record("AUV-250101-CLAIMDEAD");
        dispatcher.dispatchBatch();
        OffsetDateTime now = OffsetDateTime.now();
        events.claim(deadId, now, now);
        events.markDead(deadId, now, "spent");
        dueAt(sentId, now.minusMinutes(1));
        dueAt(deadId, now.minusMinutes(1));

        assertThat(events.claim(sentId, now, now.plusMinutes(2))).isZero();
        assertThat(events.claim(deadId, now, now.plusMinutes(2))).isZero();
    }

    @Test
    void anOutcomeIsRefusedForARowThatNoWorkerHasClaimed() {
        UUID eventId = record("AUV-250101-UNCLAIMED");
        OffsetDateTime now = OffsetDateTime.now();

        assertThat(events.markSent(eventId, now)).isZero();
        assertThat(events.markDead(eventId, now, "never claimed")).isZero();

        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void aClaimedRowIsUntouchableUntilItsLeaseRunsOutAndIsThenReclaimed() {
        UUID eventId = record("AUV-250101-LEASED");
        OffsetDateTime now = OffsetDateTime.now();
        assertThat(events.claim(eventId, now, now.plusMinutes(2))).isOne();

        assertThat(dispatcher.dispatchBatch()).isZero();
        assertThat(sender.messages()).isEmpty();
        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.IN_FLIGHT);

        dueAt(eventId, OffsetDateTime.now().minusSeconds(1));

        assertThat(dispatcher.dispatchBatch()).isOne();
        assertThat(sender.messages()).hasSize(1);
        assertThat(events.findById(eventId).orElseThrow()).satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
            assertThat(event.getAttempts()).isEqualTo(2);
        });
    }

    @Test
    void noSchedulerRunsUnderTheTestConfiguration() {
        assertThat(context.getBeanNamesForType(OutboxScheduler.class)).isEmpty();
    }
}
