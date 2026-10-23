package com.auvan.api.inventory.service;

import com.auvan.api.inventory.config.RollingScheduleProperties;
import com.auvan.api.inventory.dto.DepartureLine;
import com.auvan.api.inventory.dto.SchedulePlanRequest;
import com.auvan.api.inventory.entity.DayTemplate;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.repository.DayTemplateRepository;
import com.auvan.api.inventory.repository.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class RollingSchedule {
    private final RollingScheduleProperties properties;
    private final DayTemplateRepository templates;
    private final SchedulePlanner planner;
    private final TripRepository trips;

    public RollingSchedule(RollingScheduleProperties properties, DayTemplateRepository templates,
                           SchedulePlanner planner, TripRepository trips) {
        this.properties = properties;
        this.templates = templates;
        this.planner = planner;
        this.trips = trips;
    }

    /** Fills the coming days from the template; departures that already exist or clash are skipped. */
    @Transactional
    public int topUp(OffsetDateTime now) {
        Optional<DayTemplate> template = templates.findByNameIgnoreCase(properties.template());
        if (template.isEmpty() || template.get().getDepartures().isEmpty()) {
            return 0;
        }
        LocalDate today = now.atZoneSameInstant(SchedulePlanner.BANGKOK).toLocalDate();
        List<LocalDate> dates = today.datesUntil(today.plusDays(properties.daysAhead())).toList();
        List<DepartureLine> lines = template.get().getDepartures().stream().map(DepartureLine::from).toList();
        List<Trip> created = planner.plan(new SchedulePlanRequest(dates, lines, null), now).creatable().stream()
                .map(slot -> new Trip(slot.route(), slot.vehicle(), slot.departureAt()))
                .toList();
        trips.saveAll(created);
        return created.size();
    }
}
