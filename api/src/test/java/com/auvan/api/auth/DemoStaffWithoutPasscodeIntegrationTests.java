package com.auvan.api.auth;

import com.auvan.api.AuthenticationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"auth.demo.enabled=true", "auth.demo.staff-passcode="})
@AutoConfigureMockMvc
class DemoStaffWithoutPasscodeIntegrationTests extends AuthenticationTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void withNoPasscodeConfiguredTheStaffEndpointIsNotFoundButStudentsStillSignIn() throws Exception {
        mockMvc.perform(post("/api/v1/auth/demo/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passcode\":\"anything\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/auth/demo/student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("STUDENT"));
    }
}
