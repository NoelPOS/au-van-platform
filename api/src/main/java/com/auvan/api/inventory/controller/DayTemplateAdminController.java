package com.auvan.api.inventory.controller;

import com.auvan.api.inventory.dto.DayTemplateRequest;
import com.auvan.api.inventory.dto.DayTemplateResponse;
import com.auvan.api.inventory.service.DayTemplateService;
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
@RequestMapping("/api/v1/admin/day-templates")
public class DayTemplateAdminController {
    private final DayTemplateService templates;

    public DayTemplateAdminController(DayTemplateService templates) {
        this.templates = templates;
    }

    @GetMapping
    public List<DayTemplateResponse> list() {
        return templates.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DayTemplateResponse create(@Valid @RequestBody DayTemplateRequest request) {
        return templates.create(request);
    }

    @PutMapping("/{id}")
    public DayTemplateResponse update(@PathVariable UUID id, @Valid @RequestBody DayTemplateRequest request) {
        return templates.update(id, request);
    }

    @PostMapping("/{id}/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        templates.delete(id);
    }
}
