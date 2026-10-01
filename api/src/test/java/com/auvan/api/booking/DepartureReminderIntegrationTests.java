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
import com.auvan.api.outbox.entity.OutboxStatus;
import com.auvan.api.outbox.repository.OutboxEventRepository;
import com.auvan.api.outbox.service.OutboxDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class DepartureReminderIntegrationTests extends AuthenticationTestSupport {
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
    private DepartureReminderService reminders;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PaymentProofService paymentProofs;

    @Autowired
    private PaymentProofReviewService review;

    @Autowired
    private BookingExpiryService expiry;

    @Autowired
    private OutboxDispatcher dispatcher;

    @Autowired
    private OutboxEventRepository events;

    @Autowired
    private RecordingLineMessageSender sender;

    @Autowired
    private InMemoryPaymentProofStorage storage;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private BookingRepository bookings;

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

    private UUID student;
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

    @Test
    void approvingABookingSchedulesBothDepartureRemindersForTheStudent() {
        Trip trip = tripDepartingIn(Duration.ofDays(3));
        UUID bookingId = approvedBooking(trip, "key-both");

        assertThat(reminderOfType(OutboxEventType.DEPARTURE_REMINDER_24H)).satisfies(event -> {
            assertThat(event.getAggregateId()).isEqualTo(bookingId);
            assertThat(event.getRecipientUserId()).isEqualTo(student);
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getAttempts()).isZero();
            assertThat(event.getDedupeKey()).isEqualTo(bookingId + ":DEPARTURE_REMINDER_24H");
            assertThat(event.getNextAttemptAt()).isCloseTo(
                    trip.getDepartureAt().minusHours(24), within(1, ChronoUnit.SECONDS));
            assertThat(event.getPayload()).contains("\"origin\":\"AU\"", "\"destination\":\"Asok\"")
                    .containsPattern("\"seats\":\\[\"A").containsPattern("\"departureAt\":\"20");
        });
        assertThat(reminderOfType(OutboxEventType.DEPARTURE_REMINDER_1H)).satisfies(event -> {
            assertThat(event.getDedupeKey()).isEqualTo(bookingId + ":DEPARTURE_REMINDER_1H");
            assertThat(event.getNextAttemptAt()).isCloseTo(
                    trip.getDepartureAt().minusHours(1), within(1, ChronoUnit.SECONDS));
        });
    }

    @Test
    void aReminderWhoseMomentHasAlreadyPassedIsNotQueuedAtAll() {
        UUID bookingId = approvedBooking(tripDepartingIn(Duration.ofHours(12)), "key-twelve");

        assertThat(remindersOf(bookingId)).extracting(OutboxEvent::getEventType)
                .containsExactly(OutboxEventType.DEPARTURE_REMINDER_1H);
    }

    @Test
    void aBookingApprovedInsideTheLastHourGetsNoReminderAtAll() {
        UUID bookingId = approvedBooking(tripDepartingIn(Duration.ofMinutes(30)), "key-thirty");

        assertThat(remindersOf(bookingId)).isEmpty();
    }

    @Test
    void schedulingTheSameRemindersAgainLeavesOneRowPerOffset() {
        Trip trip = tripDepartingIn(Duration.ofDays(3));
        UUID bookingId = approvedBooking(trip, "key-twice");

        int queuedAgain = transactions.execute(status ->
                reminders.schedule(bookings.findById(bookingId).orElseThrow(), OffsetDateTime.now()));

        assertThat(queuedAgain).isZero();
        assertThat(remindersOf(bookingId)).hasSize(2);
    }

    @Test
    void theDedupeKeyIsUniqueSoASecondRowCarryingItCannotBeStoredAtAll() {
        Trip trip = tripDepartingIn(Duration.ofDays(3));
        UUID bookingId = approvedBooking(trip, "key-constraint");
        OutboxEvent scheduled = reminderOfType(OutboxEventType.DEPARTURE_REMINDER_24H);

        assertThatThrownBy(() -> insertRowCarrying(scheduled.getDedupeKey()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(remindersOf(bookingId)).hasSize(2);
    }

    @Test
    void aReminderIsNotDispatchedBeforeItsTimeAndIsDispatchedAfterIt() {
        UUID bookingId = approvedBooking(tripDepartingIn(Duration.ofDays(3)), "key-due");
        OutboxEvent reminder = reminderOfType(OutboxEventType.DEPARTURE_REMINDER_1H);
        drainDueNotifications();

        assertThat(dispatcher.dispatchBatch()).isZero();
        assertThat(sender.messages()).isEmpty();

        dueAt(reminder.getId(), OffsetDateTime.now().minusSeconds(1));

        assertThat(dispatcher.dispatchBatch()).isOne();
        assertThat(sender.messages()).singleElement().satisfies(message -> {
            assertThat(message.to()).isEqualTo("Ustudent-reminder");
            assertThat(message.message().altText()).contains("departs in 1 hour").contains("AU to Asok");
            assertThat(message.retryKey()).isEqualTo(reminder.getId().toString());
        });
        assertThat(events.findById(reminder.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(remindersOf(bookingId)).hasSize(2);
    }

    @Test
    void cancellingABookingKillsItsUnsentRemindersAndLeavesItsCancellationMessageAlone() {
        UUID bookingId = approvedBooking(tripDepartingIn(Duration.ofDays(3)), "key-cancel");

        bookingService.cancel(student, bookingId);

        assertThat(remindersOf(bookingId)).hasSize(2).allSatisfy(reminder -> {
            assertThat(reminder.getStatus()).isEqualTo(OutboxStatus.DEAD);
            assertThat(reminder.getLastError()).contains("no longer eligible");
            assertThat(reminder.getProcessedAt()).isNotNull();
        });
        assertThat(eventsOfType(OutboxEventType.BOOKING_CANCELLED)).singleElement().satisfies(cancelled ->
                assertThat(cancelled.getStatus()).isEqualTo(OutboxStatus.PENDING));

        dueAt(remindersOf(bookingId).getFirst().getId(), OffsetDateTime.now().minusSeconds(1));
        dispatcher.dispatchBatch();
        assertThat(sender.messages()).noneSatisfy(message ->
                assertThat(message.message().altText()).contains("departs in"));
    }

    @Test
    void aReminderAlreadyDueButNotYetClaimedIsStillWithdrawnByCancellation() {
        UUID bookingId = approvedBooking(tripDepartingIn(Duration.ofDays(3)), "key-due-cancel");
        OutboxEvent reminder = reminderOfType(OutboxEventType.DEPARTURE_REMINDER_1H);
        drainDueNotifications();
        dueAt(reminder.getId(), OffsetDateTime.now().minusSeconds(1));
        assertThat(events.findById(reminder.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PENDING);

        bookingService.cancel(student, bookingId);

        assertThat(events.findById(reminder.getId()).orElseThrow()).satisfies(withdrawn -> {
            assertThat(withdrawn.getStatus()).isEqualTo(OutboxStatus.DEAD);
            assertThat(withdrawn.getLastError()).contains("no longer eligible");
        });

        dispatcher.dispatchBatch();
        assertThat(sender.messages()).noneSatisfy(message ->
                assertThat(message.message().altText()).contains("departs in"));
    }

    @Test
    void expiringABookingKillsItsUnsentReminders() {
        Trip trip = tripDepartingIn(Duration.ofDays(3));
        UUID bookingId = createBooking(trip, "key-expire", trip.getSeats().getFirst());
        transactions.executeWithoutResult(status ->
                reminders.schedule(bookings.findById(bookingId).orElseThrow(), OffsetDateTime.now()));
        assertThat(remindersOf(bookingId)).hasSize(2);
        overdue(bookingId);

        assertThat(expiry.sweep()).isOne();

        assertThat(remindersOf(bookingId)).hasSize(2).allSatisfy(reminder ->
                assertThat(reminder.getStatus()).isEqualTo(OutboxStatus.DEAD));
        assertThat(eventsOfType(OutboxEventType.BOOKING_EXPIRED)).singleElement().satisfies(expired ->
                assertThat(expired.getStatus()).isEqualTo(OutboxStatus.PENDING));
    }

    @Test
    void aStateChangeNotificationBackingOffAfterAFailureIsNotWithdrawnWithTheReminders() {
        UUID bookingId = approvedBooking(tripDepartingIn(Duration.ofDays(3)), "key-backoff");
        sender.failNext(1);
        dispatcher.dispatchBatch();
        OutboxEvent backingOff = events.findAll().stream()
                .filter(event -> event.getDedupeKey() == null && event.getAttempts() > 0)
                .findFirst().orElseThrow();
        assertThat(backingOff.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(backingOff.getNextAttemptAt()).isAfter(OffsetDateTime.now());

        bookingService.cancel(student, bookingId);

        assertThat(events.findById(backingOff.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.PENDING);
        assertThat(remindersOf(bookingId)).allSatisfy(reminder ->
                assertThat(reminder.getStatus()).isEqualTo(OutboxStatus.DEAD));
    }

    @Test
    void aReminderThatHasAlreadyBeenSentIsNotTouchedByTheWithdrawal() {
        UUID bookingId = approvedBooking(tripDepartingIn(Duration.ofDays(3)), "key-sent");
        OutboxEvent reminder = reminderOfType(OutboxEventType.DEPARTURE_REMINDER_1H);
        drainDueNotifications();
        dueAt(reminder.getId(), OffsetDateTime.now().minusSeconds(1));
        assertThat(dispatcher.dispatchBatch()).isOne();

        bookingService.cancel(student, bookingId);

        assertThat(events.findById(reminder.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    private UUID approvedBooking(Trip trip, String idempotencyKey) {
        UUID bookingId = createBooking(trip, idempotencyKey, trip.getSeats().getFirst());
        paymentProofs.submit(student, bookingId, jpeg("the-slip"));
        UUID proofId = proofs.findAll().stream()
                .filter(proof -> proof.getBooking().getId().equals(bookingId)).findFirst().orElseThrow().getId();
        review.approve(administrator, proofId, "Received in full.");
        return bookingId;
    }

    private UUID createBooking(Trip trip, String idempotencyKey, TripSeat seat) {
        UUID holdId = UUID.randomUUID();
        claims.save(new SeatClaim(seat, student, holdId, OffsetDateTime.now().plus(Duration.ofMinutes(10))));
        bookingService.create(student, idempotencyKey, new CreateBookingRequest(holdId, "Somchai P.", "0812345678"));
        return bookings.findAll().stream().filter(booking -> booking.getTrip().getId().equals(trip.getId()))
                .map(Booking::getId).findFirst().orElseThrow();
    }

    private void drainDueNotifications() {
        while (dispatcher.dispatchBatch() > 0) {
        }
        sender.reset();
    }

    private void dueAt(UUID eventId, OffsetDateTime when) {
        jdbc.update("update outbox_events set next_attempt_at = ? where id = ?", when, eventId);
    }

    private void overdue(UUID bookingId) {
        jdbc.update("update bookings set payment_deadline_at = ? where id = ?",
                OffsetDateTime.now().minusMinutes(5), bookingId);
    }

    @Transactional
    void insertRowCarrying(String dedupeKey) {
        jdbc.update("""
                insert into outbox_events (id, event_type, aggregate_id, recipient_user_id, payload, status,
                                           attempts, next_attempt_at, dedupe_key, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(), OutboxEventType.DEPARTURE_REMINDER_24H.name(), UUID.randomUUID(), student,
                "{}", OutboxStatus.PENDING.name(), 0, OffsetDateTime.now().plusDays(1), dedupeKey,
                OffsetDateTime.now());
    }

    private List<OutboxEvent> remindersOf(UUID bookingId) {
        return events.findAll().stream()
                .filter(event -> event.getAggregateId().equals(bookingId) && event.getDedupeKey() != null)
                .sorted((left, right) -> left.getNextAttemptAt().compareTo(right.getNextAttemptAt()))
                .toList();
    }

    private OutboxEvent reminderOfType(OutboxEventType type) {
        return events.findAll().stream().filter(event -> event.getEventType() == type).findFirst().orElseThrow();
    }

    private List<OutboxEvent> eventsOfType(OutboxEventType type) {
        return events.findAll().stream().filter(event -> event.getEventType() == type).toList();
    }

    private static MockMultipartFile jpeg(String content) {
        return new MockMultipartFile("file", "slip.jpg", "image/jpeg", content.getBytes(StandardCharsets.UTF_8));
    }

    private Trip tripDepartingIn(Duration untilDeparture) {
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
