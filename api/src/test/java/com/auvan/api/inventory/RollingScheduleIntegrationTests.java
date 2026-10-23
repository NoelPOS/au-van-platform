package com.auvan.api.inventory;

import com.auvan.api.inventory.entity.DayTemplate;
import com.auvan.api.inventory.entity.DayTemplateDeparture;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.service.RollingSchedule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
class RollingScheduleIntegrationTests extends ScheduleTestSupport {
    @Autowired
    RollingSchedule schedule;

    @Test
    void fillsTheNextWeekFromTheTemplateAndSkipsWhatAlreadyExists() {
        saveTemplate("weekday", "08:00", "17:00");
        LocalDate today = LocalDate.of(2030, 3, 4);
        OffsetDateTime now = at(today, "12:00");
        tripAt(today.plusDays(1), "08:00");

        int created = schedule.topUp(now);

        assertThat(created).isEqualTo(12);
        assertThat(departures()).hasSize(13)
                .contains(at(today, "17:00"), at(today.plusDays(6), "08:00"), at(today.plusDays(6), "17:00"))
                .doesNotContain(at(today, "08:00"), at(today.plusDays(7), "08:00"));
        assertThat(schedule.topUp(now)).isZero();
    }

    @Test
    void leavesACancelledDepartureCancelled() {
        saveTemplate("Weekday", "08:00");
        LocalDate today = LocalDate.of(2030, 3, 4);
        Trip cancelled = tripAt(today.plusDays(2), "08:00");
        cancelled.cancel("Driver off sick");
        trips.save(cancelled);

        schedule.topUp(at(today, "06:00"));

        assertThat(departures()).hasSize(7).containsOnlyOnce(at(today.plusDays(2), "08:00"));
    }

    @Test
    void doesNothingWithoutTheTemplate() {
        saveTemplate("Weekend", "08:00");

        assertThat(schedule.topUp(at(LocalDate.of(2030, 3, 4), "06:00"))).isZero();
        assertThat(trips.findAll()).isEmpty();
    }

    private void saveTemplate(String name, String... clocks) {
        List<DayTemplateDeparture> departures = Arrays.stream(clocks)
                .map(clock -> new DayTemplateDeparture(LocalTime.parse(clock), route, van))
                .toList();
        templates.save(new DayTemplate(name, departures));
    }

    private List<OffsetDateTime> departures() {
        return trips.findAll().stream()
                .map(trip -> trip.getDepartureAt().atZoneSameInstant(BANGKOK).toOffsetDateTime())
                .toList();
    }
}
