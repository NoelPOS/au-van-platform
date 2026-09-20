package com.auvan.api.inventory.controller;

import com.auvan.api.inventory.dto.CreateSeatLayoutRequest;
import com.auvan.api.inventory.dto.SeatLayoutResponse;
import com.auvan.api.inventory.dto.UpdateSeatLayoutRequest;
import com.auvan.api.inventory.service.SeatLayoutService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/seat-layouts")
public class SeatLayoutAdminController {
    private final SeatLayoutService seatLayoutService;

    public SeatLayoutAdminController(SeatLayoutService seatLayoutService) {
        this.seatLayoutService = seatLayoutService;
    }

    @GetMapping
    public List<SeatLayoutResponse> list() {
        return seatLayoutService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SeatLayoutResponse create(@Valid @RequestBody CreateSeatLayoutRequest request) {
        return seatLayoutService.create(request);
    }

    @PutMapping("/{id}")
    public SeatLayoutResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateSeatLayoutRequest request) {
        return seatLayoutService.update(id, request);
    }
}
