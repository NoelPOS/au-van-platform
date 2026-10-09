package com.auvan.api.inventory;

import com.auvan.api.inventory.entity.RouteStatus;
import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.inventory.entity.VehicleStatus;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScheduleApplyIntegrationTests extends ScheduleTestSupport {
    static final String PREVIEW = "/api/v1/admin/schedule/preview";
    static final String APPLY = "/api/v1/admin/schedule/apply";

    @Test
    void applyingCreatesExactlyThePreviewedTripsAndAReplayCreatesNoneTwice() throws Exception {
        LocalDate first = today().plusDays(1);
        String plan = plan(List.of(first, first.plusDays(1)), line("07:00", van), line("16:30", van));
        String hash = JsonPath.read(postJson(PREVIEW, plan)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departures.length()").value(4))
                .andExpect(jsonPath("$.departures[*].outcome").value(
                        everyItem(is("CREATE"))))
                .andReturn().getResponse().getContentAsString(), "$.planHash");
        assertThat(trips.count()).isZero();

        String applied = apply("key-1", withHash(plan, hash))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.created").value(4))
                .andExpect(jsonPath("$.skipped").value(0))
                .andReturn().getResponse().getContentAsString();
        assertThat(trips.findAll()).extracting(trip -> trip.getDepartureAt().toInstant()).containsExactlyInAnyOrder(
                at(first, "07:00").toInstant(), at(first, "16:30").toInstant(),
                at(first.plusDays(1), "07:00").toInstant(), at(first.plusDays(1), "16:30").toInstant());

        apply("key-1", withHash(plan, hash))
                .andExpect(status().isCreated())
                .andExpect(content().json(applied));
        assertThat(trips.count()).isEqualTo(4);

        postJson(PREVIEW, plan)
                .andExpect(jsonPath("$.departures[*].outcome").value(
                        everyItem(is("CLASH"))))
                .andExpect(jsonPath("$.departures[0].reason").value("VAN-01 is already on the 07:00 AU → Asok."));
    }

    @Test
    void aPlanThatChangedSincePreviewIsRefusedAndCreatesNothing() throws Exception {
        LocalDate day = today().plusDays(1);
        String plan = plan(List.of(day), line("07:00", van), line("09:00", van));
        String hash = JsonPath.read(postJson(PREVIEW, plan).andReturn().getResponse().getContentAsString(),
                "$.planHash");
        tripAt(day, "09:00");

        apply("key-1", withHash(plan, hash))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("schedule_changed"));
        assertThat(trips.count()).isOne();
    }

    @Test
    void previewFlagsPastTimesPastDaysAndAVanAlreadyOutOnAnotherRun() throws Exception {
        LocalDate tomorrow = today().plusDays(1);
        tripAt(tomorrow, "06:45");
        postJson(PREVIEW, plan(List.of(today().minusDays(1), today(), tomorrow), line("00:00", van),
                line("07:00", van), line("08:00", van)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departures.length()").value(9))
                .andExpect(jsonPath("$.departures[0].outcome").value("PAST"))
                .andExpect(jsonPath("$.departures[0].reason").value("This day has already passed."))
                .andExpect(jsonPath("$.departures[3].date").value(today().toString()))
                .andExpect(jsonPath("$.departures[3].outcome").value("PAST"))
                .andExpect(jsonPath("$.departures[3].reason").value("00:00 has already passed."))
                .andExpect(jsonPath("$.departures[6].outcome").value("CREATE"))
                .andExpect(jsonPath("$.departures[7].time").value("07:00"))
                .andExpect(jsonPath("$.departures[7].outcome").value("CLASH"))
                .andExpect(jsonPath("$.departures[7].reason").value("VAN-01 is already on the 06:45 AU → Asok."))
                .andExpect(jsonPath("$.departures[8].outcome").value("CREATE"));
    }

    @Test
    void twoLinesForOneVanThatOverlapClashWithEachOther() throws Exception {
        postJson(PREVIEW, plan(List.of(today().plusDays(1)), line("07:00", van), line("07:30", van)))
                .andExpect(jsonPath("$.departures[0].outcome").value("CREATE"))
                .andExpect(jsonPath("$.departures[1].outcome").value("CLASH"))
                .andExpect(jsonPath("$.departures[1].reason").value("VAN-01 is already on the 07:00 AU → Asok."));
    }

    @Test
    void aCancelledTripBlocksOnlyItsExactDepartureTime() throws Exception {
        LocalDate day = today().plusDays(1);
        Trip cancelled = tripAt(day, "07:00");
        cancelled.cancel("Van in for repairs.");
        trips.save(cancelled);

        postJson(PREVIEW, plan(List.of(day), line("07:00", van), line("07:30", van)))
                .andExpect(jsonPath("$.departures[0].outcome").value("CLASH"))
                .andExpect(jsonPath("$.departures[1].outcome").value("CREATE"));
    }

    @Test
    void anInactiveRouteOrVanIsReportedAndSkipped() throws Exception {
        SeatLayout layout = seatLayouts.save(new SeatLayout("Spare", List.of(new SeatLayoutSeat("A1", 1, 1))));
        Vehicle retired = vehicles.save(new Vehicle("VAN-09", "Old", layout));
        retired.update(retired.getCode(), retired.getName(), layout, VehicleStatus.INACTIVE);
        vehicles.save(retired);

        postJson(PREVIEW, plan(List.of(today().plusDays(1)), line("07:00", retired)))
                .andExpect(jsonPath("$.departures[0].outcome").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.departures[0].reason").value("VAN-09 is out of service."));

        route.update(route.getOrigin(), route.getDestination(), route.getFare(), route.getDurationMinutes(),
                RouteStatus.INACTIVE);
        routes.save(route);
        postJson(PREVIEW, plan(List.of(today().plusDays(1)), line("07:00", van)))
                .andExpect(jsonPath("$.departures[0].outcome").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.departures[0].reason").value("AU → Asok is inactive."));
    }

    @Test
    void applyNeedsAnIdempotencyKeyAndAPreviewedPlan() throws Exception {
        String plan = plan(List.of(today().plusDays(1)), line("07:00", van));

        postJson(APPLY, withHash(plan, "anything"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("idempotency_key_required"));
        apply("key-1", plan)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("preview_required"));
        postJson(PREVIEW, plan(List.of(), line("07:00", van)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Choose at least one day."));
        assertThat(trips.count()).isZero();
    }

    @Test
    void studentsCannotPreviewOrApply() throws Exception {
        String studentToken = tokenFor("student-token", "Ustudent", false);
        String plan = plan(List.of(today().plusDays(1)), line("07:00", van));

        postJson(PREVIEW, plan, studentToken).andExpect(status().isForbidden());
        postJson(APPLY, withHash(plan, "anything"), studentToken).andExpect(status().isForbidden());
    }

    ResultActions apply(String key, String body) throws Exception {
        return mockMvc.perform(post(APPLY)
                .header("Authorization", "Bearer " + adminToken)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    static String line(String clock, Vehicle vehicle) {
        return "{\"time\":\"%s\",\"routeId\":\"%%ROUTE%%\",\"vehicleId\":\"%s\"}".formatted(clock, vehicle.getId());
    }

    String plan(List<LocalDate> dates, String... lines) {
        String days = String.join(",", dates.stream().map(date -> "\"" + date + "\"").toList());
        return "{\"dates\":[%s],\"departures\":[%s]}".formatted(days, String.join(",", lines))
                .replace("%ROUTE%", route.getId().toString());
    }

    static String withHash(String plan, String hash) {
        return plan.substring(0, plan.length() - 1) + ",\"planHash\":\"" + hash + "\"}";
    }
}
