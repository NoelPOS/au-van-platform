package com.auvan.api.live;

import com.auvan.api.live.dto.LiveSignal;
import com.auvan.api.live.service.LiveSignalPublisher;
import com.auvan.api.live.service.LiveStreams;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.service.OutboxRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LiveSignalDeliveryIntegrationTests extends LiveUpdatesTestSupport {
    @Autowired
    private LiveSignalPublisher publisher;

    @Autowired
    private OutboxRecorder outbox;

    @Autowired
    private LiveStreams streams;

    @Autowired
    private TransactionTemplate transactions;

    private SignedIn studentA;
    private SignedIn studentB;
    private SignedIn administrator;

    @BeforeEach
    void setUp() throws Exception {
        users.deleteAll();
        studentA = student("Ulive-a");
        studentB = student("Ulive-b");
        administrator = admin("Ulive-admin");
    }

    @Test
    void aSignalReachesTheStreamOnceItsTransactionCommits() throws Exception {
        var stream = stream(studentA);
        UUID bookingId = UUID.randomUUID();

        transactions.executeWithoutResult(status -> {
            publisher.publish(LiveSignal.booking(bookingId, studentA.id()));
            assertThat(contentOf(stream)).doesNotContain(bookingId.toString());
        });

        assertThat(contentOf(stream)).contains("data:{\"kind\":\"booking\",\"id\":\"" + bookingId + "\"}");
    }

    @Test
    void aSignalFromARolledBackTransactionNeverReachesTheStream() throws Exception {
        var stream = stream(studentA);
        UUID bookingId = UUID.randomUUID();

        transactions.executeWithoutResult(status -> {
            publisher.publish(LiveSignal.booking(bookingId, studentA.id()));
            status.setRollbackOnly();
        });

        assertThat(contentOf(stream)).doesNotContain(bookingId.toString());
    }

    @Test
    void aStudentNeverReceivesAnotherStudentsBookingSignal() throws Exception {
        var streamA = stream(studentA);
        var streamB = stream(studentB);
        var adminStream = stream(administrator);
        UUID bookingOfA = UUID.randomUUID();

        transactions.executeWithoutResult(status -> outbox.record(OutboxEventType.PAYMENT_APPROVED, bookingOfA,
                studentA.id(), Map.of("detail", "Payment approved."), OffsetDateTime.now()));

        assertThat(contentOf(streamA)).contains(bookingOfA.toString());
        assertThat(contentOf(adminStream)).contains(bookingOfA.toString());
        assertThat(contentOf(streamB)).doesNotContain(bookingOfA.toString());
    }

    @Test
    void everyoneReceivesATripsSeatSignal() throws Exception {
        var streamA = stream(studentA);
        var streamB = stream(studentB);
        UUID tripId = UUID.randomUUID();

        transactions.executeWithoutResult(status -> publisher.publish(LiveSignal.trip(tripId)));

        assertThat(contentOf(streamA)).contains("\"kind\":\"trip\",\"id\":\"" + tripId + "\"");
        assertThat(contentOf(streamB)).contains(tripId.toString());
    }

    @Test
    void aSignalNamesItsKindAndIdAndNeverTheOwner() throws Exception {
        var stream = stream(studentA);

        transactions.executeWithoutResult(status ->
                publisher.publish(LiveSignal.booking(UUID.randomUUID(), studentA.id())));

        assertThat(contentOf(stream)).doesNotContain(studentA.id().toString());
    }

    @Test
    void anIdleStreamIsKeptOpenByAHeartbeat() throws Exception {
        var stream = stream(studentA);

        streams.heartbeat();

        assertThat(contentOf(stream)).contains(":heartbeat");
    }

    private static String contentOf(org.springframework.mock.web.MockHttpServletResponse stream) {
        try {
            return stream.getContentAsString();
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
