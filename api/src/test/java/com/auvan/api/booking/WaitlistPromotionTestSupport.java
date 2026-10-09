package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.JoinWaitlistRequest;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.entity.WaitlistEntry;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.booking.service.BookingExpiryService;
import com.auvan.api.booking.service.BookingService;
import com.auvan.api.booking.service.SeatAvailabilityService;
import com.auvan.api.booking.service.SeatHoldService;
import com.auvan.api.booking.service.WaitlistPromotionService;
import com.auvan.api.booking.service.WaitlistService;
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
import com.auvan.api.outbox.RecordingLineMessageSender;
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.repository.OutboxEventRepository;
import com.auvan.api.outbox.service.OutboxDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(WaitlistPromotionTestSupport.FakeSenderConfiguration.class)
abstract class WaitlistPromotionTestSupport extends AuthenticationTestSupport {
    @TestConfiguration
    static class FakeSenderConfiguration {
        @Bean
        @Primary
        RecordingLineMessageSender recordingLineMessageSender() {
            return new RecordingLineMessageSender();
        }
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    WaitlistPromotionService promotion;

    @Autowired
    WaitlistService waitlistService;

    @Autowired
    BookingService bookingService;

    @Autowired
    BookingExpiryService expiry;

    @Autowired
    SeatHoldService holds;

    @Autowired
    OutboxDispatcher dispatcher;

    @Autowired
    RecordingLineMessageSender sender;

    @MockitoSpyBean
    WaitlistEntryRepository waitlist;

    @MockitoSpyBean
    SeatAvailabilityService availability;

    @Autowired
    SeatClaimRepository claims;

    @Autowired
    BookingRepository bookings;

    @Autowired
    OutboxEventRepository events;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeys;

    @Autowired
    AppUserRepository users;

    @Autowired
    private TripRepository trips;

    @Autowired
    private VehicleRepository vehicles;

    @Autowired
    private SeatLayoutRepository seatLayouts;

    @Autowired
    private VanRouteRepository routes;

    @Autowired
    JdbcTemplate jdbc;

    Trip trip;
    UUID studentA;
    UUID studentB;
    UUID holder;

    @BeforeEach
    void setUp() {
        clearData();
        sender.reset();
        trip = createTrip("VAN-WP1", OffsetDateTime.now().plusDays(1), 2);
        studentA = users.save(new AppUser("Uwait-a", "Waiting Student A")).getId();
        studentB = users.save(new AppUser("Uwait-b", "Waiting Student B")).getId();
        holder = users.save(new AppUser("Uwait-holder", "Holding Student")).getId();
    }

    @AfterEach
    void clearData() {
        events.deleteAll();
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

    UUID promoteFirstStudentOntoSeatZero() {
        holdSeat(holder, 0, Duration.ofHours(2));
        holdSeat(holder, 1, Duration.ofHours(2));
        UUID first = join(studentA, trip, 1);
        lapseHoldOn(0);
        assertThat(promotion.sweep()).isOne();
        return first;
    }

    UUID join(UUID userId, Trip on, int seatsWanted) {
        return waitlistService.join(userId, new JoinWaitlistRequest(on.getId(), seatsWanted)).id();
    }

    WaitlistEntry entry(UUID entryId) {
        return waitlist.findById(entryId).orElseThrow();
    }

    TripSeat seat(int index) {
        return trip.getSeats().get(index);
    }

    void holdSeat(UUID userId, int seatIndex, Duration ttl) {
        claims.save(new SeatClaim(seat(seatIndex), userId, UUID.randomUUID(), OffsetDateTime.now().plus(ttl)));
    }

    void lapseHoldOn(int seatIndex) {
        lapseHoldOn(seat(seatIndex));
    }

    void lapseHoldOn(TripSeat seat) {
        jdbc.update("update seat_claims set expires_at = ? where trip_seat_id = ?",
                OffsetDateTime.now().minusMinutes(1), seat.getId());
    }

    void lapsePromotion(UUID entryId) {
        jdbc.update("update waitlist_entries set promotion_expires_at = ? where id = ?",
                OffsetDateTime.now().minusMinutes(1), entryId);
    }

    List<OutboxEvent> outboxOfType(OutboxEventType type) {
        return events.findAll().stream().filter(event -> event.getEventType() == type).toList();
    }

    void fillEverySeatOf(Trip on) {
        UUID filler = users.save(new AppUser("Ufiller-" + on.getId(), "Holding Student")).getId();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusHours(2);
        on.getSeats().forEach(seat -> claims.save(new SeatClaim(seat, filler, UUID.randomUUID(), expiresAt)));
    }

    Trip createTrip(String vehicleCode, OffsetDateTime departureAt, int seatCount) {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= seatCount; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout " + vehicleCode, layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle(vehicleCode, "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, departureAt));
    }
}
