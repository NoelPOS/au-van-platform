package com.auvan.api.auth.controller;

import com.auvan.api.auth.dto.AuthResponse;
import com.auvan.api.auth.dto.DemoStaffRequest;
import com.auvan.api.auth.dto.DemoStudentRequest;
import com.auvan.api.auth.service.DemoSignInService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// SECURITY CONTROL: switched off, these endpoints do not exist and answer 404.
@RestController
@RequestMapping("/api/v1/auth/demo")
@ConditionalOnProperty(prefix = "auth.demo", name = "enabled", havingValue = "true")
public class DemoSignInController {
    private final DemoSignInService demoSignIn;

    DemoSignInController(DemoSignInService demoSignIn) {
        this.demoSignIn = demoSignIn;
    }

    @PostMapping("/student")
    public ResponseEntity<AuthResponse> signInStudent(@RequestBody(required = false) DemoStudentRequest request) {
        return ResponseEntity.ok(demoSignIn.signInStudent(request == null ? null : request.studentId()));
    }

    @PostMapping("/staff")
    public ResponseEntity<AuthResponse> signInStaff(@Valid @RequestBody DemoStaffRequest request) {
        return ResponseEntity.ok(demoSignIn.signInStaff(request.passcode()));
    }
}
