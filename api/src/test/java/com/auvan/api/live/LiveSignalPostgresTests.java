package com.auvan.api.live;

import com.auvan.api.PostgresTestSupport;
import com.auvan.api.live.dto.LiveSignal;
import com.auvan.api.live.service.LiveSignalPublisher;
import com.auvan.api.live.service.LiveSignalPublisherImpl;
import com.auvan.api.live.service.StreamTicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@SpringBootTest(properties = "live-updates.postgres-notify=true")
@AutoConfigureMockMvc
class LiveSignalPostgresTests extends PostgresTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private LiveSignalPublisher publisher;

    @Autowired
    private StreamTicketService tickets;

    @Autowired
    private TransactionTemplate transactions;

    @Test
    void signalsTravelThroughPostgresListenAndNotify() {
        assertThat(publisher).isInstanceOf(LiveSignalPublisherImpl.class);
    }

    @Test
    void aCommittedSignalReachesTheStreamThroughTheDatabase() throws Exception {
        UUID student = UUID.randomUUID();
        var stream = streamFor(student);
        UUID bookingId = UUID.randomUUID();

        transactions.executeWithoutResult(status -> publisher.publish(LiveSignal.booking(bookingId, student)));

        awaitContent(stream, bookingId);
    }

    @Test
    void aRolledBackSignalNeverLeavesTheDatabase() throws Exception {
        UUID student = UUID.randomUUID();
        var stream = streamFor(student);
        UUID rolledBack = UUID.randomUUID();
        UUID committedAfter = UUID.randomUUID();

        transactions.executeWithoutResult(status -> {
            publisher.publish(LiveSignal.booking(rolledBack, student));
            status.setRollbackOnly();
        });
        transactions.executeWithoutResult(status -> publisher.publish(LiveSignal.booking(committedAfter, student)));

        awaitContent(stream, committedAfter);
        assertThat(stream.getContentAsString()).doesNotContain(rolledBack.toString());
    }

    @Test
    void anotherStudentsStreamNeverCarriesTheSignal() throws Exception {
        UUID owner = UUID.randomUUID();
        var ownersStream = streamFor(owner);
        var othersStream = streamFor(UUID.randomUUID());
        UUID bookingId = UUID.randomUUID();

        transactions.executeWithoutResult(status -> publisher.publish(LiveSignal.booking(bookingId, owner)));

        awaitContent(ownersStream, bookingId);
        assertThat(othersStream.getContentAsString()).doesNotContain(bookingId.toString());
    }

    private MockHttpServletResponse streamFor(UUID userId) throws Exception {
        Jwt principal = Jwt.withTokenValue("access-token").header("alg", "HS256")
                .subject(userId.toString()).claim("role", "STUDENT").issuedAt(Instant.now()).build();
        return mockMvc.perform(get("/api/v1/events").param("ticket", tickets.issue(principal)))
                .andExpect(request().asyncStarted())
                .andReturn().getResponse();
    }

    private static void awaitContent(MockHttpServletResponse stream, UUID expected) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (!stream.getContentAsString().contains(expected.toString()) && Instant.now().isBefore(deadline)) {
            Thread.sleep(50);
        }
        assertThat(stream.getContentAsString()).contains(expected.toString());
    }
}
