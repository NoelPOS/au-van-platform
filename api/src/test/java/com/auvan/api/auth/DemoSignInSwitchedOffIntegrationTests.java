package com.auvan.api.auth;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class DemoSignInSwitchedOffIntegrationTests extends AuthenticationTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Test
    void bothDemoEndpointsAreNotFoundWhileDemoSignInIsOff() throws Exception {
        long before = users.count();

        mockMvc.perform(post("/api/v1/auth/demo/student"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/auth/demo/staff")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passcode\":\"anything\"}"))
                .andExpect(status().isNotFound());

        assertThat(users.count()).isEqualTo(before);
    }
}
