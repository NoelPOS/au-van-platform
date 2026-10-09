package com.auvan.api.inventory;

import com.auvan.api.booking.service.IdempotencyService;
import com.auvan.api.inventory.dto.DepartureLine;
import com.auvan.api.inventory.dto.SchedulePlanRequest;
import com.auvan.api.inventory.repository.TripRepository;
import com.auvan.api.inventory.service.ScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockingDetails;

// Not @Transactional: a test transaction puts every thread on one connection and hides the race.
@SpringBootTest
@AutoConfigureMockMvc
@MockitoSpyBean(types = TripRepository.class)
class ScheduleConcurrencyIntegrationTests extends ScheduleTestSupport {
    @Autowired
    private ScheduleService schedule;

    private UUID admin;
    private SchedulePlanRequest request;

    @BeforeEach
    void planTomorrow() {
        admin = users.findByLineSubject("Uadmin").orElseThrow().getId();
        LocalDate tomorrow = today().plusDays(1);
        List<DepartureLine> lines = List.of(
                new DepartureLine(LocalTime.of(7, 0), route.getId(), van.getId()),
                new DepartureLine(LocalTime.of(16, 30), route.getId(), van.getId()));
        SchedulePlanRequest unsigned = new SchedulePlanRequest(List.of(tomorrow, tomorrow.plusDays(1)), lines, null);
        request = new SchedulePlanRequest(unsigned.dates(), lines, schedule.preview(unsigned).planHash());
    }

    @Test
    void aRivalApplyThatCommitsFirstLeavesTheLoserWithAConflictAndNoDuplicates() throws Exception {
        rivalCommitsWhileTheFirstApplyIsPlanning("rival-key");

        assertThatThrownBy(() -> schedule.apply(admin, "first-key", request))
                .isInstanceOfSatisfying(ResponseStatusException.class, refusal -> {
                    assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(refusal.getBody().getProperties()).containsEntry("code", "schedule_changed");
                });

        assertThat(trips.count()).isEqualTo(4);
        assertThat(idempotencyKeys.count()).isOne();
    }

    @Test
    void aDuplicateInFlightApplyIsAnsweredWithTheResponseTheWinnerStored() throws Exception {
        AtomicReference<IdempotencyService.StoredResponse> winner = rivalCommitsWhileTheFirstApplyIsPlanning("one-key");

        IdempotencyService.StoredResponse duplicate = schedule.apply(admin, "one-key", request);

        assertThat(duplicate).isEqualTo(winner.get());
        assertThat(duplicate.status()).isEqualTo(201);
        assertThat(trips.count()).isEqualTo(4);
        assertThat(idempotencyKeys.count()).isOne();
    }

    private AtomicReference<IdempotencyService.StoredResponse> rivalCommitsWhileTheFirstApplyIsPlanning(String key) {
        AtomicBoolean first = new AtomicBoolean(true);
        AtomicReference<IdempotencyService.StoredResponse> rival = new AtomicReference<>();
        doAnswer(invocation -> {
            Object existing = real(invocation);
            if (first.compareAndSet(true, false)) {
                try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
                    rival.set(pool.submit(() -> schedule.apply(admin, key, request)).get(30, TimeUnit.SECONDS));
                }
            }
            return existing;
        }).when(trips).findForVehiclesBetween(any(), any(), any());
        return rival;
    }

    private static Object real(InvocationOnMock invocation) throws Throwable {
        return mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }
}
