package com.auvan.api.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LineWebhookController {
    @PostMapping("/api/line/webhook")
    public ResponseEntity<Void> receive() {
        return ResponseEntity.ok().build();
    }
}
