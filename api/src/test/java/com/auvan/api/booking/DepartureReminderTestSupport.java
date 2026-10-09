package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.PaymentProofRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.service.BookingExpiryService;
import com.auvan.api.booking.service.BookingService;
import com.auvan.api.booking.service.DepartureReminderService;
import com.auvan.api.booking.service.PaymentProofReviewService;
import com.auvan.api.booking.service.PaymentProofService;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SpringBootTest
@Import(DepartureReminderTestSupport.FakePortsConfiguration.class)
abstract class DepartureReminderTestSupport extends AuthenticationTestSupport {
    @TestConfiguration
    static class FakePortsConfiguration {
        @Bean
        @Primary
        InMemoryPaymentProofStorage inMemoryPaymentProofStorage() {
            return new InMemoryPaymentProofStorage();
        }

        @Bean
        @Primary
        RecordingLineMessageSender recordingLineMessageSender() {
            return new RecordingLineMessageSender();
        }
    }

    @Autowired
    DepartureReminderService reminders;

    @Autowired
    BookingService bookingService;

    @Autowired
    private PaymentProofService paymentProofs;

    @Autowired
    private PaymentProofReviewService review;

    @Autowired
    BookingExpiryService expiry;

    @Autowired
    OutboxDispatcher dispatcher;

    @Autowired
    OutboxEventRepository events;

    @Autowired
    RecordingLineMessageSender sender;

    @Autowired
    private InMemoryPaymentProofStorage storage;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    BookingRepository bookings;

    @Autowired
    private SeatClaimRepository claims;

    @Autowired
    private PaymentProofRepository proofs;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeys;

    @Autowired
    private TripRepository trips;

    @Autowired
    private VehicleRepository vehicles;

    @Autowired
    private SeatLayoutRepository seatLayouts;

    @Autowired
    private VanRouteRepository routes;

    UUID student;
    private UUID administrator;
    private int layoutSequence;

    @BeforeEach
    void setUp() {
        clearData();
        sender.reset();
        storage.reset();
        layoutSequence = 0;
        student = users.save(new AppUser("Ustudent-reminder", "Student")).getId();
        administrator = users.save(new AppUser("Uadmin-reminder", "Administrator")).getId();
    }

    @AfterEach
    void clearData() {
        events.deleteAll();
        proofs.deleteAll();
        claims.deleteAll();
        bookings.deleteAll();
        idempotencyKeys.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    UUID approvedBooking(Trip trip, String idempotencyKey) {
        UUID bookingId = createBooking(trip, idempotencyKey, trip.getSeats().getFirst());
        paymentProofs.submit(student, bookingId, jpeg("the-slip"));
        UUID proofId = proofs.findAll().stream()
                .filter(proof -> proof.getBooking().getId().equals(bookingId)).findFirst().orElseThrow().getId();
        review.approve(administrator, proofId, "Received in full.");
        return bookingId;
    }

    UUID createBooking(Trip trip, String idempotencyKey, TripSeat seat) {
        UUID holdId = UUID.randomUUID();
        claims.save(new SeatClaim(seat, student, holdId, OffsetDateTime.now().plus(Duration.ofMinutes(10))));
        bookingService.create(student, idempotencyKey, new CreateBookingRequest(holdId, "Somchai P.", "0812345678"));
        return bookings.findAll().stream().filter(booking -> booking.getTrip().getId().equals(trip.getId()))
                .map(Booking::getId).findFirst().orElseThrow();
    }

    void drainDueNotifications() {
        while (dispatcher.dispatchBatch() > 0) {
        }
        sender.reset();
    }

    void dueAt(UUID eventId, OffsetDateTime when) {
        jdbc.update("update outbox_events set next_attempt_at = ? where id = ?", when, eventId);
    }

    List<OutboxEvent> remindersOf(UUID bookingId) {
        return events.findAll().stream()
                .filter(event -> event.getAggregateId().equals(bookingId) && event.getDedupeKey() != null)
                .sorted((left, right) -> left.getNextAttemptAt().compareTo(right.getNextAttemptAt()))
                .toList();
    }

    OutboxEvent reminderOfType(OutboxEventType type) {
        return events.findAll().stream().filter(event -> event.getEventType() == type).findFirst().orElseThrow();
    }

    private static MockMultipartFile jpeg(String content) {
        return new MockMultipartFile("file", "slip.jpg", "image/jpeg", content.getBytes(StandardCharsets.UTF_8));
    }

    Trip tripDepartingIn(Duration untilDeparture) {
        layoutSequence++;
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 4; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Reminder layout " + layoutSequence, layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-REM-" + layoutSequence, "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plus(untilDeparture)));
    }
}
