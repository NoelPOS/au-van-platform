package com.auvan.api.inventory;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DayTemplateIntegrationTests extends ScheduleTestSupport {
    private static final String TEMPLATES = "/api/v1/admin/day-templates";

    @Test
    void anAdministratorCreatesListsRenamesAndDeletesATemplate() throws Exception {
        String id = JsonPath.read(postJson(TEMPLATES, weekday("Weekday"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Weekday"))
                .andExpect(jsonPath("$.departures[0].time").value("07:00"))
                .andExpect(jsonPath("$.departures[1].time").value("16:30"))
                .andExpect(jsonPath("$.departures[1].routeId").value(route.getId().toString()))
                .andExpect(jsonPath("$.departures[1].vehicleId").value(van.getId().toString()))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put(TEMPLATES + "/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Exam week","departures":[
                                  {"time":"09:15","routeId":"%s","vehicleId":"%s"}]}
                                """.formatted(route.getId(), van.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Exam week"))
                .andExpect(jsonPath("$.departures.length()").value(1));

        mockMvc.perform(get(TEMPLATES).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].departures[0].time").value("09:15"));

        postJson(TEMPLATES + "/" + id + "/delete", "").andExpect(status().isNoContent());
        assertThat(templates.count()).isZero();
        postJson(TEMPLATES + "/" + id + "/delete", "").andExpect(status().isNotFound());
    }

    @Test
    void aSecondTemplateWithTheSameNameInAnyCaseIsRefused() throws Exception {
        postJson(TEMPLATES, weekday("Weekday")).andExpect(status().isCreated());

        postJson(TEMPLATES, weekday("weekday"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("template_name_taken"));
        assertThat(templates.count()).isOne();
    }

    @Test
    void renamingOntoAnotherTemplatesNameIsRefused() throws Exception {
        postJson(TEMPLATES, weekday("Weekday")).andExpect(status().isCreated());
        String id = JsonPath.read(postJson(TEMPLATES, weekday("Saturday"))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put(TEMPLATES + "/" + id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weekday("WEEKDAY")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("template_name_taken"));
    }

    @Test
    void oneVanLeavingTwiceAtTheSameTimeIsRefused() throws Exception {
        postJson(TEMPLATES, """
                {"name":"Doubled","departures":[
                  {"time":"07:00","routeId":"%1$s","vehicleId":"%2$s"},
                  {"time":"07:00","routeId":"%1$s","vehicleId":"%2$s"}]}
                """.formatted(route.getId(), van.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("template_van_twice"));
        assertThat(templates.count()).isZero();
    }

    @Test
    void aTemplateNeedsANameADepartureAndKnownRoutesAndVans() throws Exception {
        postJson(TEMPLATES, """
                {"name":" ","departures":[{"time":"07:00","routeId":"%s","vehicleId":"%s"}]}
                """.formatted(route.getId(), van.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Give the template a name."));
        postJson(TEMPLATES, "{\"name\":\"Empty\",\"departures\":[]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Add at least one departure."));
        postJson(TEMPLATES, """
                {"name":"Ghost","departures":[{"time":"07:00","routeId":"%s","vehicleId":"%s"}]}
                """.formatted(van.getId(), van.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Route not found."));
        assertThat(templates.count()).isZero();
    }

    @Test
    void studentsCannotReadOrWriteTemplates() throws Exception {
        String studentToken = tokenFor("student-token", "Ustudent", false);

        mockMvc.perform(get(TEMPLATES).header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        postJson(TEMPLATES, weekday("Weekday"), studentToken).andExpect(status().isForbidden());
        assertThat(templates.count()).isZero();
    }

    private String weekday(String name) {
        return """
                {"name":"%1$s","departures":[
                  {"time":"16:30","routeId":"%2$s","vehicleId":"%3$s"},
                  {"time":"07:00","routeId":"%2$s","vehicleId":"%3$s"}]}
                """.formatted(name, route.getId(), van.getId());
    }
}
