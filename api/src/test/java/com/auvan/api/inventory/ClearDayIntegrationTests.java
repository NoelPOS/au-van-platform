package com.auvan.api.inventory;

import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.repository.TripRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@MockitoSpyBean(types = TripRepository.class)
class ClearDayIntegrationTests extends ScheduleTestSupport {
    private static final String CLEAR_DAY = "/api/v1/admin/schedule/clear-day";

    private LocalDate tomorrow;
    private UUID studentId;

    @BeforeEach
    void createStudent() {
        tomorrow = today().plusDays(1);
        studentId = users.save(new AppUser("Ustudent", "Student")).getId();
    }

    @Test
    void clearingADayRemovesOnlyDeparturesNobodyHasClaimed() throws Exception {
        Trip unbooked = tripAt(tomorrow, "07:00");
        Trip booked = tripAt(tomorrow, "09:00");
        Trip held = tripAt(tomorrow, "11:00");
        Trip queued = tripAt(tomorrow, "13:00");
        Trip otherDay = tripAt(tomorrow.plusDays(1), "07:00");
        OffsetDateTime now = OffsetDateTime.now();
        bookings.save(new Booking(booked, studentId, "AUV-TEST-1", "Student", "0800000000",
                new BigDecimal("35.00"), now.plusHours(2), now));
        claims.save(new SeatClaim(held.getSeats().getFirst(), studentId, UUID.randomUUID(), now.minusMinutes(1)));
        waitlist.save(new WaitlistEntry(queued, studentId, 1, now));

        postJson(CLEAR_DAY, "{\"date\":\"" + tomorrow + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removed").value(1))
                .andExpect(jsonPath("$.kept").value(3));

        assertThat(trips.findAll()).extracting(Trip::getId)
                .containsExactlyInAnyOrder(booked.getId(), held.getId(), queued.getId(), otherDay.getId())
                .doesNotContain(unbooked.getId());
    }

    @Test
    void departuresThatHaveAlreadyLeftTodayAreLeftAlone() throws Exception {
        Trip departed = tripAt(today(), "00:00");

        postJson(CLEAR_DAY, "{\"date\":\"" + today() + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removed").value(0))
                .andExpect(jsonPath("$.kept").value(0));
        assertThat(trips.findById(departed.getId())).isPresent();
    }

    @Test
    void aDayThatHasPassedCannotBeCleared() throws Exception {
        tripAt(today().minusDays(1), "07:00");

        postJson(CLEAR_DAY, "{\"date\":\"" + today().minusDays(1) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("day_passed"));
        assertThat(trips.count()).isOne();
    }

    @Test
    void aHoldThatLandsDuringClearingStopsTheWholeClearRatherThanFailing() throws Exception {
        Trip unbooked = tripAt(tomorrow, "07:00");
        Trip claimedMeanwhile = tripAt(tomorrow, "09:00");
        claims.save(new SeatClaim(claimedMeanwhile.getSeats().getFirst(), studentId, UUID.randomUUID(),
                OffsetDateTime.now().plusMinutes(5)));
        doReturn(List.of(unbooked, claimedMeanwhile)).when(trips).findUnclaimedBetween(any(), any());

        postJson(CLEAR_DAY, "{\"date\":\"" + tomorrow + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("day_changed"));
        assertThat(trips.count()).isEqualTo(2);
    }

    @Test
    void studentsCannotClearADay() throws Exception {
        tripAt(tomorrow, "07:00");
        String studentToken = tokenFor("student-token", "Ustudent2", false);

        postJson(CLEAR_DAY, "{\"date\":\"" + tomorrow + "\"}", studentToken).andExpect(status().isForbidden());
        assertThat(trips.count()).isOne();
    }
}
