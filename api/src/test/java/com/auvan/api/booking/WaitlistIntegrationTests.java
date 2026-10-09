package com.auvan.api.booking;

import com.auvan.api.booking.dto.JoinWaitlistRequest;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.entity.WaitlistStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WaitlistIntegrationTests extends WaitlistTestSupport {
    @Test
    void joiningAFullTripReturnsTheFirstPlaceAndTheNextStudentTheSecond() throws Exception {
        join(studentToken, fullTrip, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.tripId").value(fullTrip.getId().toString()))
                .andExpect(jsonPath("$.seatsWanted").value(1))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.position").value(1));

        join(otherToken, fullTrip, 2)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatsWanted").value(2))
                .andExpect(jsonPath("$.position").value(2));
        assertThat(waitlist.count()).isEqualTo(2);
    }

    @Test
    void joiningTwiceReturnsTheSameEntryRatherThanASecondOne() throws Exception {
        String first = entryIdFrom(join(studentToken, fullTrip, 1).andExpect(status().isCreated()));
        join(otherToken, fullTrip, 1).andExpect(status().isCreated());

        join(studentToken, fullTrip, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(first))
                .andExpect(jsonPath("$.position").value(1));
        assertThat(waitlist.count()).isEqualTo(2);
    }

    @Test
    void leavingWithdrawsTheEntryAndTheStudentBehindMovesUp() throws Exception {
        String entryId = entryIdFrom(join(studentToken, fullTrip, 1).andExpect(status().isCreated()));
        join(otherToken, fullTrip, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(2));

        mockMvc.perform(post("/api/v1/waitlist/" + entryId + "/leave")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNoContent());

        assertThat(waitlist.findById(UUID.fromString(entryId)).orElseThrow().getStatus())
                .isEqualTo(WaitlistStatus.WITHDRAWN);
        assertThat(waitlist.count()).isEqualTo(2);
        mockMvc.perform(get("/api/v1/waitlist").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].position").value(1));
    }

    @Test
    void aStudentsOwnWaitlistReadReturnsTheirEntryAndItsPosition() throws Exception {
        join(otherToken, fullTrip, 1).andExpect(status().isCreated());
        String entryId = entryIdFrom(join(studentToken, fullTrip, 3).andExpect(status().isCreated()));

        mockMvc.perform(get("/api/v1/waitlist").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(entryId))
                .andExpect(jsonPath("$[0].tripId").value(fullTrip.getId().toString()))
                .andExpect(jsonPath("$[0].seatsWanted").value(3))
                .andExpect(jsonPath("$[0].status").value("WAITING"))
                .andExpect(jsonPath("$[0].position").value(2));
    }

    @Test
    void aWithdrawnEntryDropsOutOfTheStudentsOwnRead() throws Exception {
        String entryId = entryIdFrom(join(studentToken, fullTrip, 1).andExpect(status().isCreated()));

        leave(studentToken, entryId).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/waitlist").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rejoiningAfterLeavingReusesTheRowAndGoesToTheBack() throws Exception {
        String entryId = entryIdFrom(join(studentToken, fullTrip, 1).andExpect(status().isCreated()));
        leave(studentToken, entryId).andExpect(status().isNoContent());
        join(otherToken, fullTrip, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.position").value(1));

        join(studentToken, fullTrip, 1)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(entryId))
                .andExpect(jsonPath("$.status").value("WAITING"))
                .andExpect(jsonPath("$.position").value(2));
        assertThat(waitlist.count()).isEqualTo(2);
    }

    @Test
    void theUniqueConstraintRefusesASecondRowForTheSameStudentAndTrip() {
        UUID student = users.findByLineSubject("Ustudent").orElseThrow().getId();
        OffsetDateTime now = OffsetDateTime.now();
        waitlist.saveAndFlush(new WaitlistEntry(fullTrip, student, 1, now));

        assertThatThrownBy(() -> waitlist.saveAndFlush(new WaitlistEntry(fullTrip, student, 1, now)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aJoinThatLosesTheRaceToInsertIsAConflictRatherThanACrash() {
        UUID student = users.findByLineSubject("Ustudent").orElseThrow().getId();
        WaitlistEntry rival = waitlist.saveAndFlush(new WaitlistEntry(fullTrip, student, 2, OffsetDateTime.now()));
        doReturn(Optional.empty()).when(waitlist).findByTripIdAndUserId(fullTrip.getId(), student);

        assertThatThrownBy(() -> waitlistService.join(student, new JoinWaitlistRequest(fullTrip.getId(), 1)))
                .isInstanceOfSatisfying(ResponseStatusException.class, conflict -> {
                    assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(conflict.getReason()).contains("refresh");
                });

        assertThat(waitlist.findAll()).singleElement()
                .satisfies(entry -> assertThat(entry.getId()).isEqualTo(rival.getId()));
    }
}
