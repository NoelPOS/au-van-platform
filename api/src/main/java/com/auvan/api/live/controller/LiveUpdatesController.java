package com.auvan.api.live.controller;

import com.auvan.api.live.dto.StreamTicketResponse;
import com.auvan.api.live.service.LiveStreams;
import com.auvan.api.live.service.StreamTicketService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/events")
public class LiveUpdatesController {
    private final StreamTicketService tickets;
    private final LiveStreams streams;

    public LiveUpdatesController(StreamTicketService tickets, LiveStreams streams) {
        this.tickets = tickets;
        this.streams = streams;
    }

    @PostMapping("/ticket")
    public StreamTicketResponse ticket(@AuthenticationPrincipal Jwt jwt) {
        return new StreamTicketResponse(tickets.issue(jwt));
    }

    @GetMapping
    public SseEmitter stream(@RequestParam String ticket) {
        return streams.open(tickets.verify(ticket));
    }
}
