package com.auvan.api.inventory.service;

import com.auvan.api.booking.exception.Problems;
import com.auvan.api.inventory.dto.DayTemplateRequest;
import com.auvan.api.inventory.dto.DayTemplateResponse;
import com.auvan.api.inventory.dto.DepartureLine;
import com.auvan.api.inventory.entity.DayTemplate;
import com.auvan.api.inventory.entity.DayTemplateDeparture;
import com.auvan.api.inventory.entity.VanRoute;
import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.inventory.repository.DayTemplateRepository;
import com.auvan.api.inventory.repository.VanRouteRepository;
import com.auvan.api.inventory.repository.VehicleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class DayTemplateService {
    private static final String NAME_TAKEN = "A template with that name already exists.";

    private final DayTemplateRepository templates;
    private final VanRouteRepository routes;
    private final VehicleRepository vehicles;

    public DayTemplateService(DayTemplateRepository templates, VanRouteRepository routes, VehicleRepository vehicles) {
        this.templates = templates;
        this.routes = routes;
        this.vehicles = vehicles;
    }

    @Transactional(readOnly = true)
    public List<DayTemplateResponse> list() {
        return templates.findAllByOrderByNameAsc().stream().map(DayTemplateResponse::from).toList();
    }

    @Transactional
    public DayTemplateResponse create(DayTemplateRequest request) {
        String name = request.name().trim();
        if (templates.existsByNameIgnoreCase(name)) {
            throw Problems.conflict("template_name_taken", NAME_TAKEN);
        }
        return save(new DayTemplate(name, departuresOf(request.departures())));
    }

    @Transactional
    public DayTemplateResponse update(UUID id, DayTemplateRequest request) {
        DayTemplate template = find(id);
        String name = request.name().trim();
        if (templates.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw Problems.conflict("template_name_taken", NAME_TAKEN);
        }
        template.replace(name, departuresOf(request.departures()));
        return save(template);
    }

    @Transactional
    public void delete(UUID id) {
        templates.delete(find(id));
    }

    private DayTemplateResponse save(DayTemplate template) {
        try {
            DayTemplate saved = templates.save(template);
            // Flush inside the try: @UuidGenerator defers the insert past this catch.
            templates.flush();
            return DayTemplateResponse.from(saved);
        } catch (DataIntegrityViolationException duplicate) {
            throw Problems.conflict("template_name_taken", NAME_TAKEN, duplicate);
        }
    }

    private DayTemplate find(UUID id) {
        return templates.findById(id)
                .orElseThrow(() -> Problems.notFound("template_not_found", "Template not found."));
    }

    private List<DayTemplateDeparture> departuresOf(List<DepartureLine> lines) {
        Set<String> seen = new HashSet<>();
        for (DepartureLine line : lines) {
            if (!seen.add(line.vehicleId() + "@" + line.time())) {
                throw Problems.badRequest("template_van_twice",
                        "A van can only leave once at " + line.time() + ". Change the time or the van.");
            }
        }
        return lines.stream().sorted(Comparator.comparing(DepartureLine::time)).map(this::departureOf).toList();
    }

    private DayTemplateDeparture departureOf(DepartureLine line) {
        VanRoute route = routes.findById(line.routeId())
                .orElseThrow(() -> Problems.badRequest("schedule_reference_missing", "Route not found."));
        Vehicle vehicle = vehicles.findById(line.vehicleId())
                .orElseThrow(() -> Problems.badRequest("schedule_reference_missing", "Van not found."));
        return new DayTemplateDeparture(line.time(), route, vehicle);
    }
}
