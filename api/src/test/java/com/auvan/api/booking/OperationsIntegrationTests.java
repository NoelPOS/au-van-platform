package com.auvan.api.booking;

import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OperationsIntegrationTests extends OperationsTestSupport {
    @Test
    void theDeadLetterViewReportsOnlyTheRowsTheDispatcherGaveUpOn() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        UUID aggregate = UUID.randomUUID();
        UUID dead = deadLetter(aggregate, firstStudent, "LINE refused the push: 400 invalid recipient.");
        outbox.save(new OutboxEvent(OutboxEventType.BOOKING_CREATED, UUID.randomUUID(), secondStudent,
                "{}", now));
        UUID inFlight = outbox.save(new OutboxEvent(OutboxEventType.PAYMENT_APPROVED, UUID.randomUUID(),
                thirdStudent, "{}", now.minusMinutes(1))).getId();
        assertThat(outbox.claim(inFlight, now, now.plusMinutes(5))).isEqualTo(1);

        mockMvc.perform(get("/api/v1/admin/operations/dead-letters")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(dead.toString()))
                .andExpect(jsonPath("$[0].eventType").value("BOOKING_CANCELLED"))
                .andExpect(jsonPath("$[0].aggregateId").value(aggregate.toString()))
                .andExpect(jsonPath("$[0].recipientUserId").value(firstStudent.toString()))
                .andExpect(jsonPath("$[0].attempts").value(1))
                .andExpect(jsonPath("$[0].lastError").value("LINE refused the push: 400 invalid recipient."))
                .andExpect(jsonPath("$[0].processedAt").isNotEmpty());
    }

    @Test
    void aStudentTokenIsForbiddenOnEveryOperationsEndpoint() throws Exception {
        mockMvc.perform(get(tripPath(trip)).header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/operations/dead-letters")
                        .header("Authorization", bearer(studentToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAnonymousRequestIsUnauthorizedOnEveryOperationsEndpoint() throws Exception {
        mockMvc.perform(get(tripPath(trip))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/operations/dead-letters")).andExpect(status().isUnauthorized());
    }

    // A minute back: the column is coarser than the clock, so a row claimed now can read as not due.
    private UUID deadLetter(UUID aggregateId, UUID recipient, String error) {
        OffsetDateTime now = OffsetDateTime.now();
        UUID id = outbox.save(new OutboxEvent(OutboxEventType.BOOKING_CANCELLED, aggregateId, recipient,
                "{\"reference\":\"AUV-OP-0001\"}", now.minusMinutes(1))).getId();
        assertThat(outbox.claim(id, now, now.plusMinutes(5))).isEqualTo(1);
        assertThat(outbox.markDead(id, now, error)).isEqualTo(1);
        return id;
    }
}
