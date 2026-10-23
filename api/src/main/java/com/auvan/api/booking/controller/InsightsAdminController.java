package com.auvan.api.booking.controller;

import com.auvan.api.booking.dto.InsightsResponse;
import com.auvan.api.booking.service.InsightsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/insights")
public class InsightsAdminController {
    private final InsightsService insights;

    public InsightsAdminController(InsightsService insights) {
        this.insights = insights;
    }

    @GetMapping
    public InsightsResponse between(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        return insights.between(from, to);
    }
}
