package com.auvan.api.auth.service;

import com.auvan.api.auth.config.AuthProperties;
import com.auvan.api.auth.dto.AuthResponse;
import com.auvan.api.auth.dto.UserResponse;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.entity.ApplicationRole;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.exception.Problems;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DemoSignInService {
    private static final String STAFF_SUBJECT = "demo-staff";

    private final AppUserRepository users;
    private final JwtService jwtService;
    private final AuthProperties properties;

    DemoSignInService(AppUserRepository users, JwtService jwtService, AuthProperties properties) {
        this.users = users;
        this.jwtService = jwtService;
        this.properties = properties;
    }

    @Transactional
    public AuthResponse signInStudent(UUID studentId) {
        AppUser student = Optional.ofNullable(studentId)
                .flatMap(users::findById)
                .filter(user -> user.isDemo() && user.getRole() == ApplicationRole.STUDENT)
                .orElseGet(this::newStudent);
        return signedIn(student, properties.jwt().accessTokenTtl());
    }

    @Transactional
    public AuthResponse signInStaff(String passcode) {
        String expected = properties.demo().staffPasscode();
        if (expected == null || expected.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // isEqual's time depends only on its first argument's length, so that must be the caller's input.
        if (!MessageDigest.isEqual(bytes(passcode), bytes(expected))) {
            throw Problems.unauthorized("invalid_passcode", "The staff passcode is not correct.");
        }
        AppUser staff = users.findByLineSubject(STAFF_SUBJECT).orElseGet(this::newStaff);
        return signedIn(staff, properties.demo().staffTokenTtl());
    }

    private AppUser newStudent() {
        int digits = ThreadLocalRandom.current().nextInt(1000, 10000);
        return users.save(new AppUser("demo-student:" + UUID.randomUUID(), "Demo student " + digits));
    }

    private AppUser newStaff() {
        AppUser staff = new AppUser(STAFF_SUBJECT, "Demo staff");
        staff.promoteToAdmin();
        return users.save(staff);
    }

    private AuthResponse signedIn(AppUser user, Duration lifetime) {
        return new AuthResponse(jwtService.issue(user, lifetime), lifetime.toSeconds(), UserResponse.from(user));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
