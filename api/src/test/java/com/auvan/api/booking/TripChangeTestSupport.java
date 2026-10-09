package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.client.LineTokenVerifier;
import com.auvan.api.auth.client.VerifiedLineIdentity;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingEvent;
import com.auvan.api.booking.entity.BookingEventType;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.booking.service.DepartureReminderService;
import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripSeat;
import com.auvan.api.inventory.entity.VanRoute;
import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.inventory.repository.SeatLayoutRepository;
import com.auvan.api.inventory.repository.TripRepository;
import com.auvan.api.inventory.repository.VanRouteRepository;
import com.auvan.api.inventory.repository.VehicleRepository;
import com.auvan.api.outbox.repository.OutboxEventRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
abstract class TripChangeTestSupport extends AuthenticationTestSupport {
    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    private LineTokenVerifier lineTokenVerifier;

    @Autowired
    protected AppUserRepository users;

    @Autowired
    protected TripRepository trips;

    @Autowired
    protected BookingRepository bookings;

    @Autowired
    protected SeatClaimRepository claims;

    @Autowired
    protected WaitlistEntryRepository waitlist;

    @Autowired
    protected OutboxEventRepository outbox;

    @Autowired
    protected DepartureReminderService reminders;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeys;

    @Autowired
    private VehicleRepository vehicles;

    @Autowired
    private SeatLayoutRepository seatLayouts;

    @Autowired
    private VanRouteRepository routes;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    protected Trip trip;
    protected UUID adminId;
    private String adminToken;
    private String studentToken;

    @BeforeEach
    void setUpTripChange() throws Exception {
        clearTripChangeData();
        trip = createTrip();
        adminToken = tokenFor("trip-admin", "Utrip-admin", true);
        adminId = users.findByLineSubject("Utrip-admin").orElseThrow().getId();
        studentToken = tokenFor("trip-student", "Utrip-student", false);
    }

    @AfterEach
    void clearTripChangeData() {
        outbox.deleteAll();
        waitlist.deleteAll();
        claims.deleteAll();
        bookings.deleteAll();
        idempotencyKeys.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    protected Booking book(int seatIndex, BookingStatus status) {
        OffsetDateTime now = OffsetDateTime.now();
        UUID student = users.save(new AppUser("Upassenger-" + seatIndex, "Passenger " + seatIndex)).getId();
        TripSeat seat = trip.getSeats().get(seatIndex);
        Booking booking = new Booking(trip, student, "AUV-261009-PASS000" + seatIndex, "Passenger " + seatIndex,
                "0812345678", new BigDecimal("35.00"), now.plusHours(2), now);
        booking.addSeat(seat);
        booking.recordEvent(BookingEventType.CREATED, "Booked seats " + seat.getLabel() + ".", student, now);
        switch (status) {
            case PAYMENT_UNDER_REVIEW -> booking.markPaymentUnderReview(now);
            case CONFIRMED -> booking.confirm(now);
            case CANCELLED -> booking.cancel(now);
            default -> { }
        }
        Booking saved = bookings.save(booking);
        if (!saved.isCancelled()) {
            SeatClaim claim = new SeatClaim(seat, student, UUID.randomUUID(), now.plusMinutes(5));
            claim.attachTo(saved.getId());
            claims.save(claim);
        }
        return saved;
    }

    protected Booking reload(Booking booking) {
        return bookings.findById(booking.getId()).orElseThrow();
    }

    protected List<BookingEvent> eventsOf(Booking booking) {
        return transactions.execute(status -> reload(booking).getEvents());
    }

    protected WaitlistEntry queue(OffsetDateTime joinedAt) {
        UUID student = users.save(new AppUser("Uwaiting-" + UUID.randomUUID(), "Waiting Student")).getId();
        return waitlist.save(new WaitlistEntry(trip, student, 1, joinedAt));
    }

    protected void departIn(int minutes) {
        departAt(OffsetDateTime.now().plusMinutes(minutes));
    }

    protected void departAt(OffsetDateTime departureAt) {
        jdbc.update("update trips set departure_at = ? where id = ?", departureAt, trip.getId());
    }

    protected MockHttpServletRequestBuilder admin(MockHttpServletRequestBuilder request, String body) {
        return request.header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected MockHttpServletRequestBuilder asStudent(MockHttpServletRequestBuilder request, String body) {
        return request.header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected String holdAsStudent(int seatIndex) throws Exception {
        return JsonPath.read(mockMvc.perform(asStudent(post("/api/v1/seat-holds"), "{\"tripId\":\"" + trip.getId()
                        + "\",\"seatIds\":[\"" + trip.getSeats().get(seatIndex).getId() + "\"]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.holdId");
    }

    protected ResultActions confirmAsStudent(String holdId) throws Exception {
        return mockMvc.perform(asStudent(post("/api/v1/bookings"), "{\"holdId\":\"" + holdId
                        + "\",\"passengerName\":\"Somchai P.\",\"passengerPhone\":\"0812345678\"}")
                .header("Idempotency-Key", "trip-change-" + holdId));
    }

    private Trip createTrip() {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 4; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout VAN-CHANGE", layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-CHANGE", "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(2).truncatedTo(ChronoUnit.SECONDS)));
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
