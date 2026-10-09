package com.auvan.api.inventory.dto;

import com.auvan.api.inventory.entity.DayTemplate;

import java.util.List;
import java.util.UUID;

public record DayTemplateResponse(UUID id, String name, List<DepartureLine> departures) {
    public static DayTemplateResponse from(DayTemplate template) {
        return new DayTemplateResponse(template.getId(), template.getName(),
                template.getDepartures().stream().map(DepartureLine::from).toList());
    }
}
