package com.auvan.api.auth;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"auth.demo.enabled=true", "auth.demo.staff-passcode=correct-horse-battery"})
@AutoConfigureMockMvc
class DemoSignInIntegrationTests extends AuthenticationTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void clearUsers() {
        users.deleteAll();
    }

    @Test
    void aStudentWithNoIdGetsANewDemoStudentWhoseTokenWorks() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/demo/student"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.role").value("STUDENT"))
                .andExpect(jsonPath("$.user.displayName").value(matchesPattern("Demo student \\d{4}")))
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(JsonPath.read(body, "$.user.id"));
        assertThat(users.findById(id).orElseThrow().isDemo()).isTrue();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + JsonPath.read(body, "$.accessToken")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void aStudentWithAnEmptyBodyAlsoGetsANewDemoStudent() throws Exception {
        signInStudent("{}").andExpect(status().isOk());

        assertThat(users.findAll()).singleElement().satisfies(user -> assertThat(user.isDemo()).isTrue());
    }

    @Test
    void aKnownDemoStudentIdSignsTheSameStudentInAgain() throws Exception {
        UUID returning = users.save(new AppUser("demo-student:" + UUID.randomUUID(), "Demo student 4821")).getId();

        signInStudent("{\"studentId\":\"" + returning + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(returning.toString()))
                .andExpect(jsonPath("$.user.displayName").value("Demo student 4821"));

        assertThat(users.count()).isOne();
    }

    @Test
    void aLineStudentsIdNeverSignsThatStudentIn() throws Exception {
        UUID lineStudent = users.save(new AppUser("Ureal-student", "Real Student")).getId();

        signInStudent("{\"studentId\":\"" + lineStudent + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(not(lineStudent.toString())))
                .andExpect(jsonPath("$.user.role").value("STUDENT"));

        assertThat(users.count()).isEqualTo(2);
    }

    @Test
    void theDemoStaffIdNeverSignsStaffInThroughTheStudentDoor() throws Exception {
        String staffId = JsonPath.read(signInStaff("correct-horse-battery")
                .andReturn().getResponse().getContentAsString(), "$.user.id");

        signInStudent("{\"studentId\":\"" + staffId + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(not(staffId)))
                .andExpect(jsonPath("$.user.role").value("STUDENT"));
    }

    @Test
    void theRightPasscodeSignsInTheOneDemoStaffAsAnAdministrator() throws Exception {
        String body = signInStaff("correct-horse-battery")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.displayName").value("Demo staff"))
                .andReturn().getResponse().getContentAsString();
        signInStaff("correct-horse-battery")
                .andExpect(jsonPath("$.user.id").value((String) JsonPath.read(body, "$.user.id")));

        assertThat(users.count()).isOne();
        mockMvc.perform(get("/api/v1/admin/access-check")
                        .header("Authorization", "Bearer " + JsonPath.read(body, "$.accessToken")))
                .andExpect(status().isNoContent());
    }

    @Test
    void aDemoStaffTokenLastsTwoHours() throws Exception {
        String body = signInStaff("correct-horse-battery")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresIn").value(7200))
                .andReturn().getResponse().getContentAsString();

        Jwt token = jwtDecoder.decode(JsonPath.read(body, "$.accessToken"));
        assertThat(Duration.between(token.getIssuedAt(), token.getExpiresAt())).isEqualTo(Duration.ofHours(2));
    }

    @Test
    void aWrongPasscodeIsRefusedAndCreatesNoOne() throws Exception {
        signInStaff("correct-horse-batter")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_passcode"));
        signInStaff("correct-horse-battery-staple")
                .andExpect(status().isUnauthorized());

        assertThat(users.count()).isZero();
    }

    @Test
    void aBlankPasscodeIsAValidationError() throws Exception {
        signInStaff(" ").andExpect(status().isBadRequest());
    }

    private ResultActions signInStudent(String json) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/demo/student").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions signInStaff(String passcode) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/demo/staff")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passcode\":\"" + passcode + "\"}"));
    }
}
