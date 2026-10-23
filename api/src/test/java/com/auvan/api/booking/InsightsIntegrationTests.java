package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.VanRoute;
import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.inventory.repository.SeatLayoutRepository;
import com.auvan.api.inventory.repository.TripRepository;
import com.auvan.api.inventory.repository.VanRouteRepository;
import com.auvan.api.inventory.repository.VehicleRepository;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InsightsIntegrationTests extends AuthenticationTestSupport {
    private static final ZoneId BANGKOK = ZoneId.of("Asia/Bangkok");
    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);
    private static final OffsetDateTime NOW = OffsetDateTime.now();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private TripRepository trips;

    @Autowired
    private VehicleRepository vehicles;

    @Autowired
    private SeatLayoutRepository seatLayouts;

    @Autowired
    private VanRouteRepository routes;

    private VanRoute asok;
    private VanRoute mega;
    private Vehicle van;
    private String studentToken;
    private UUID studentId;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        clearData();
        asok = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        mega = routes.save(new VanRoute("AU", "Mega Bangna", new BigDecimal("50.00"), 30));
        SeatLayout layout = seatLayouts.save(new SeatLayout("Four seats", List.of(
                new SeatLayoutSeat("A1", 1, 1), new SeatLayoutSeat("A2", 1, 2),
                new SeatLayoutSeat("B1", 2, 1), new SeatLayoutSeat("B2", 2, 2))));
        van = vehicles.save(new Vehicle("VAN-INSIGHTS", "Toyota Commuter", layout));
        studentToken = tokenFor("insights-student", "Uinsights-student", false);
        studentId = users.findByLineSubject("Uinsights-student").orElseThrow().getId();
        adminToken = tokenFor("insights-admin", "Uinsights-admin", true);
    }

    @AfterEach
    void clearData() {
        bookings.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    @Test
    void revenueCountsOnlyConfirmedBookingsAndTheUnpaidAreAwaitingPayment() throws Exception {
        Trip trip = trip(asok, DAY, "08:00");
        book(trip, 2, booking -> booking.confirm(NOW));
        book(trip, 1, booking -> { });
        book(trip, 1, booking -> booking.markPaymentUnderReview(NOW));
        book(trip, 1, booking -> booking.markPaymentRejected(NOW.plusHours(1), NOW));
        book(trip, 1, booking -> booking.expire(NOW));

        DocumentContext insights = insights(DAY, DAY);

        assertThat(money(insights, "$.totals.revenue")).isEqualByComparingTo("70");
        assertThat(money(insights, "$.totals.awaitingPayment")).isEqualByComparingTo("105");
        assertThat(money(insights, "$.totals.refundsDue")).isEqualByComparingTo("0");
        assertThat(insights.read("$.totals.bookings", Integer.class)).isEqualTo(4);
        assertThat(insights.read("$.totals.seatsSold", Integer.class)).isEqualTo(2);
    }

    @Test
    void refundsOwedAndRefundsSentAreTotalledApartFromRevenue() throws Exception {
        Trip trip = trip(asok, DAY, "08:00");
        book(trip, 2, booking -> {
            booking.confirm(NOW);
            booking.cancel(NOW);
        });
        book(trip, 1, booking -> {
            booking.confirm(NOW);
            booking.cancel(NOW);
            booking.markRefunded(studentId, null, NOW);
        });
        Trip cancelled = trip(mega, DAY, "09:00");
        book(cancelled, 1, booking -> {
            booking.markPaymentUnderReview(NOW);
            booking.cancelWithTrip(NOW);
        });
        cancelled.cancel("Driver unwell");
        trips.save(cancelled);

        DocumentContext insights = insights(DAY, DAY);

        assertThat(money(insights, "$.totals.refundsDue")).isEqualByComparingTo("120");
        assertThat(money(insights, "$.totals.refunded")).isEqualByComparingTo("35");
        assertThat(money(insights, "$.totals.revenue")).isEqualByComparingTo("0");
        assertThat(insights.read("$.totals.bookings", Integer.class)).isZero();
        assertThat(insights.read("$.totals.trips", Integer.class)).isEqualTo(1);
        assertThat(insights.read("$.totals.cancelledTrips", Integer.class)).isEqualTo(1);
    }

    @Test
    void occupancyAndRouteRowsCountRunningTripsInThePeriodOnly() throws Exception {
        book(trip(asok, DAY, "08:00"), 3, booking -> booking.confirm(NOW));
        book(trip(asok, DAY, "12:00"), 1, booking -> booking.confirm(NOW));
        book(trip(mega, DAY, "09:00"), 1, booking -> booking.confirm(NOW));
        Trip cancelled = trip(mega, DAY, "18:00");
        cancelled.cancel("Road closed");
        trips.save(cancelled);
        book(trip(mega, DAY.plusDays(1), "09:00"), 4, booking -> booking.confirm(NOW));

        DocumentContext insights = insights(DAY, DAY);

        assertThat(insights.read("$.totals.seatsOffered", Integer.class)).isEqualTo(12);
        assertThat(insights.read("$.totals.seatsSold", Integer.class)).isEqualTo(5);
        assertThat(insights.read("$.totals.occupancy", Double.class)).isEqualTo(5.0 / 12);
        assertThat(money(insights, "$.totals.revenue")).isEqualByComparingTo("190");
        assertThat(insights.<List<String>>read("$.byRoute[*].destination")).containsExactly("Asok", "Mega Bangna");
        assertThat(insights.read("$.byRoute[0].routeId", String.class)).isEqualTo(asok.getId().toString());
        assertThat(insights.read("$.byRoute[0].trips", Integer.class)).isEqualTo(2);
        assertThat(insights.read("$.byRoute[0].seatsSold", Integer.class)).isEqualTo(4);
        assertThat(insights.read("$.byRoute[0].seatsOffered", Integer.class)).isEqualTo(8);
        assertThat(insights.read("$.byRoute[0].occupancy", Double.class)).isEqualTo(0.5);
        assertThat(money(insights, "$.byRoute[0].revenue")).isEqualByComparingTo("140");
        assertThat(insights.read("$.byRoute[1].trips", Integer.class)).isEqualTo(1);
        assertThat(insights.read("$.byRoute[1].occupancy", Double.class)).isEqualTo(0.25);
        assertThat(money(insights, "$.byRoute[1].revenue")).isEqualByComparingTo("50");
    }

    @Test
    void everyDayInThePeriodHasARowAndDaysFollowBangkokTime() throws Exception {
        book(trip(asok, DAY, "08:00"), 2, booking -> booking.confirm(NOW));
        book(trip(asok, DAY.plusDays(2), "00:30"), 1, booking -> booking.confirm(NOW));

        DocumentContext insights = insights(DAY, DAY.plusDays(2));

        assertThat(insights.<List<String>>read("$.byDay[*].date"))
                .containsExactly("2026-11-02", "2026-11-03", "2026-11-04");
        assertThat(money(insights, "$.byDay[0].revenue")).isEqualByComparingTo("70");
        assertThat(money(insights, "$.byDay[1].revenue")).isEqualByComparingTo("0");
        assertThat(insights.read("$.byDay[1].seatsSold", Integer.class)).isZero();
        assertThat(money(insights, "$.byDay[2].revenue")).isEqualByComparingTo("35");
        assertThat(insights.read("$.byDay[2].seatsSold", Integer.class)).isEqualTo(1);
    }

    @Test
    void aFullYearWithNoTripsReadsAsZeros() throws Exception {
        DocumentContext insights = insights(DAY, DAY.plusDays(365));

        assertThat(insights.read("$.byDay.length()", Integer.class)).isEqualTo(366);
        assertThat(insights.read("$.byRoute.length()", Integer.class)).isZero();
        assertThat(insights.read("$.totals.occupancy", Double.class)).isZero();
    }

    @Test
    void aReversedOrOverlongPeriodIsRefused() throws Exception {
        request(DAY, DAY.minusDays(1), adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("insights_range_reversed"));
        request(DAY, DAY.plusDays(366), adminToken)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("insights_range_too_long"));
    }

    @Test
    void studentsCannotReadInsights() throws Exception {
        request(DAY, DAY, studentToken).andExpect(status().isForbidden());
    }

    private DocumentContext insights(LocalDate from, LocalDate to) throws Exception {
        String body = request(from, to, adminToken)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(body);
    }

    private ResultActions request(LocalDate from, LocalDate to, String token) throws Exception {
        return mockMvc.perform(get("/api/v1/admin/insights")
                .param("from", from.toString())
                .param("to", to.toString())
                .header("Authorization", "Bearer " + token));
    }

    private static BigDecimal money(DocumentContext insights, String path) {
        return new BigDecimal(insights.read(path, Object.class).toString());
    }

    private Trip trip(VanRoute route, LocalDate date, String clock) {
        OffsetDateTime departure = date.atTime(LocalTime.parse(clock)).atZone(BANGKOK).toOffsetDateTime();
        return trips.save(new Trip(route, van, departure));
    }

    private void book(Trip trip, int seats, Consumer<Booking> outcome) {
        Booking booking = new Booking(trip, studentId, "AUV-" + UUID.randomUUID().toString().substring(0, 12),
                "Somchai P.", "0812345678", trip.getFare().multiply(BigDecimal.valueOf(seats)),
                NOW.plusHours(2), NOW);
        trip.getSeats().subList(0, seats).forEach(booking::addSeat);
        outcome.accept(booking);
        bookings.save(booking);
    }

    private String tokenFor(String idToken, String lineSubject, boolean administrator) throws Exception {
        when(lineTokenVerifier.verify(idToken)).thenReturn(new VerifiedLineIdentity(lineSubject, "Test User"));
        String response = mockMvc.perform(post("/api/v1/auth/line/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"" + idToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        if (administrator) {
            var user = users.findByLineSubject(lineSubject).orElseThrow();
            user.promoteToAdmin();
            users.save(user);
            return tokenFor(idToken + "-admin", lineSubject, false);
        }
        return JsonPath.read(response, "$.accessToken");
    }
}
