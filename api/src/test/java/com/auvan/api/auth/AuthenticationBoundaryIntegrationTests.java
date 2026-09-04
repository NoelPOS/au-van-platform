package com.auvan.api.auth;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.exception.InvalidLineTokenException;
import com.auvan.api.auth.repository.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationBoundaryIntegrationTests extends AuthenticationTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

    @BeforeEach
    void clearUsers() {
        users.deleteAll();
    }

    @Test
    void exchangesOnlyVerifiedLineIdentitiesForStudentJwt() throws Exception {
        when(lineTokenVerifier.verify("valid-line-token"))
                .thenReturn(new VerifiedLineIdentity("Uverified", "Noel"));

        var exchange = mockMvc.perform(post("/api/v1/auth/line/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"valid-line-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andReturn();

        String token = com.jayway.jsonpath.JsonPath.read(
                exchange.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Noel"))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void rejectsMissingOrInvalidCredentialsAndStudentAdminAccess() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());

        when(lineTokenVerifier.verify("invalid-line-token")).thenThrow(new InvalidLineTokenException());
        mockMvc.perform(post("/api/v1/auth/line/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"invalid-line-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("line_token_invalid"));

        when(lineTokenVerifier.verify("student-line-token"))
                .thenReturn(new VerifiedLineIdentity("Ustudent", "Student"));
        var exchange = mockMvc.perform(post("/api/v1/auth/line/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"student-line-token\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String studentToken = com.jayway.jsonpath.JsonPath.read(
                exchange.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/api/v1/admin/access-check").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
    }
}
