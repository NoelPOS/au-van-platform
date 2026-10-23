package com.auvan.api.inventory;

import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.inventory.entity.VehicleStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InventoryInUseIntegrationTests extends ScheduleTestSupport {

    @Test
    void aVanWithUpcomingTripsCannotBeMarkedInactive() throws Exception {
        tripAt(today().plusDays(1), "07:00");
        tripAt(today().plusDays(2), "07:00");

        markVan(VehicleStatus.INACTIVE)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "VAN-01 still has 2 upcoming trips. Cancel them before marking it inactive."));
        assertThat(vehicles.findById(van.getId()).orElseThrow().getStatus()).isEqualTo(VehicleStatus.ACTIVE);
    }

    @Test
    void aVanWithOnlyPastOrCancelledTripsCanBeMarkedInactive() throws Exception {
        tripAt(today().minusDays(1), "07:00");
        Trip cancelled = tripAt(today().plusDays(1), "07:00");
        cancelled.cancel("Van in for repairs.");
        trips.save(cancelled);

        markVan(VehicleStatus.INACTIVE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void aSeatLayoutNoVanUsesCanBeDeleted() throws Exception {
        SeatLayout spare = seatLayouts.save(new SeatLayout("Spare", List.of(new SeatLayoutSeat("A1", 1, 1))));

        deleteLayout(spare.getId()).andExpect(status().isNoContent());

        assertThat(seatLayouts.existsById(spare.getId())).isFalse();
    }

    @Test
    void aSeatLayoutAVanUsesCannotBeDeleted() throws Exception {
        Vehicle retired = vehicles.save(new Vehicle("VAN-02", "Old", van.getSeatLayout()));
        retired.update(retired.getCode(), retired.getName(), retired.getSeatLayout(), VehicleStatus.INACTIVE);
        vehicles.save(retired);

        deleteLayout(van.getSeatLayout().getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Used by VAN-01, VAN-02. Move them to another layout first."));
        assertThat(seatLayouts.existsById(van.getSeatLayout().getId())).isTrue();
    }

    private ResultActions markVan(VehicleStatus status) throws Exception {
        return mockMvc.perform(put("/api/v1/admin/vehicles/" + van.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code":"VAN-01","name":"Hiace","seatLayoutId":"%s","status":"%s"}
                        """.formatted(van.getSeatLayout().getId(), status)));
    }

    private ResultActions deleteLayout(UUID id) throws Exception {
        return mockMvc.perform(delete("/api/v1/admin/seat-layouts/" + id)
                .header("Authorization", "Bearer " + adminToken));
    }
}
