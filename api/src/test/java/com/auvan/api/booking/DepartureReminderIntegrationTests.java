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

/**
 * Departure reminders: when they are scheduled, when they are deliberately not,
 * and what withdraws them.
 *
 * <p>A reminder is an outbox row whose {@code next_attempt_at} is in the
 * future, so everything here is about three facts on that row — its due time,
 * its {@code dedupe_key}, and whether it still exists by the time it comes due.
 *
 * <p>Not {@code @Transactional}: the dispatch paths must run outside any
 * transaction, and a reminder's whole point is that it survives the commit that
 * scheduled it. Rows are aged with {@link JdbcTemplate} rather than by waiting,
 * for the reason {@code OutboxIntegrationTests} gives.
 */
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

    /** In foreign-key order, and in both hooks: leftovers break other classes' cleanup. */
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

    // Scheduling

    /**
     * Approval is where a trip becomes something to be reminded about, and both
     * offsets are still ahead of a booking approved three days out.
     */
    @Test
    void approvingABookingSchedulesBothDepartureRemindersForTheStudent() {
        Trip trip = tripDepartingIn(Duration.ofDays(3));
        UUID bookingId = approvedBooking(trip, "key-both");

        assertThat(reminderOfType(OutboxEventType.DEPARTURE_REMINDER_24H)).satisfies(event -> {
            assertThat(event.getAggregateId()).isEqualTo(bookingId);
            // The student, never the administrator who approved it.
            assertThat(event.getRecipientUserId()).isEqualTo(student);
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getAttempts()).isZero();
            assertThat(event.getDedupeKey()).isEqualTo(bookingId + ":DEPARTURE_REMINDER_24H");
            assertThat(event.getNextAttemptAt()).isCloseTo(
                    trip.getDepartureAt().minusHours(24), within(1, ChronoUnit.SECONDS));
            assertThat(event.getPayload()).contains("AU").contains("Asok");
        });
        assertThat(reminderOfType(OutboxEventType.DEPARTURE_REMINDER_1H)).satisfies(event -> {
            assertThat(event.getDedupeKey()).isEqualTo(bookingId + ":DEPARTURE_REMINDER_1H");
            assertThat(event.getNextAttemptAt()).isCloseTo(
                    trip.getDepartureAt().minusHours(1), within(1, ChronoUnit.SECONDS));
        });
    }

    /**
     * The legacy's rule at {@code reminder.service.ts:77}, and the reason it
     * exists: a row due in the past is a row due <em>now</em>, so queueing the
     * twenty-four hour reminder for a booking approved twelve hours before
     * departure would fire it immediately, announcing a full day's notice the
     * student has not got. Delete the skip and this reddens.
     */
    @Test
    void aReminderWhoseMomentHasAlreadyPassedIsNotQueuedAtAll() {
        UUID bookingId = approvedBooking(tripDepartingIn(Duration.ofHours(12)), "key-twelve");

        assertThat(remindersOf(bookingId)).extracting(OutboxEvent::getEventType)
                .containsExactly(OutboxEventType.DEPARTURE_REMINDER_1H);
    }

    /** Both moments gone, so there is nothing to schedule and nothing is. */
    @Test
    void aBookingApprovedInsideTheLastHourGetsNoReminderAtAll() {
        UUID bookingId = approvedBooking(tripDepartingIn(Duration.ofMinutes(30)), "key-thirty");

        assertThat(remindersOf(bookingId)).isEmpty();
    }

    /**
     * Idempotence, ported from the legacy's {@code unique (bookingId, type)}:
     * scheduling the same reminders again is a no-op rather than a second
     * message.
     */
    @Test
    void schedulingTheSameRemindersAgainLeavesOneRowPerOffset() {
        Trip trip = tripDepartingIn(Duration.ofDays(3));
        UUID bookingId = approvedBooking(trip, "key-twice");

        int queuedAgain = transactions.execute(status ->
                reminders.schedule(bookings.findById(bookingId).orElseThrow(), OffsetDateTime.now()));

        assertThat(queuedAgain).isZero();
        assertThat(remindersOf(bookingId)).hasSize(2);
    }

    /**
     * The constraint underneath that idempotence, which is what actually holds
     * when two transactions try it at once — the {@code existsByDedupeKey} read
     * above only keeps them from reaching it. Drop
     * {@code outbox_events_dedupe_key_unique} from {@code V7} and this reddens:
     * the second row is simply stored, and a student gets two of the same
     * reminder.
     */
    @Test
    void theDedupeKeyIsUniqueSoASecondRowCarryingItCannotBeStoredAtAll() {
        Trip trip = tripDepartingIn(Duration.ofDays(3));
        UUID bookingId = approvedBooking(trip, "key-constraint");
        OutboxEvent scheduled = reminderOfType(OutboxEventType.DEPARTURE_REMINDER_24H);

        assertThatThrownBy(() -> insertRowCarrying(scheduled.getDedupeKey()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(remindersOf(bookingId)).hasSize(2);
    }

    // Delivery

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
            assertThat(message.text()).contains("departs in 1 hour").contains("AU to Asok");
            assertThat(message.retryKey()).isEqualTo(reminder.getId().toString());
        });
        assertThat(events.findById(reminder.getId()).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(remindersOf(bookingId)).hasSize(2);
    }

    // Withdrawal

    /**
     * The failure this prevents is concrete: without it a student who cancelled
     * a confirmed booking is told, hours later, that their trip departs in an
     * hour. Delete the {@code reminders.cancel} call from
     * {@code BookingService.cancel} and this reddens.
     *
     * <p>The second assertion is the other half, and the reason
     * {@code cancelScheduled} is scoped to rows carrying a dedupe key: the
     * cancellation's own message is written in the same transaction and the
     * student does still need it.
     */
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

        // And nothing due ever reaches the student for the reminders.
        dueAt(remindersOf(bookingId).getFirst().getId(), OffsetDateTime.now().minusSeconds(1));
        dispatcher.dispatchBatch();
        assertThat(sender.messages()).noneSatisfy(message ->
                assertThat(message.text()).contains("departs in"));
    }

    /**
     * The same withdrawal on the expiry path. Today a booking cannot both carry
     * a reminder and be expirable — reminders are scheduled at approval, which
     * leaves the booking {@code CONFIRMED}, and the sweep only takes the three
     * unpaid statuses — so the fixture schedules the reminder against an unpaid
     * booking directly. The hook is symmetry rather than a live path, and the
     * pull request says so; what this proves is that it works if a future
     * transition ever makes the two overlap.
     */
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

    /**
     * The clause that scopes the withdrawal to reminders, in the case where it
     * is the only thing doing the work.
     *
     * <p>A state-change notification that failed once is {@code PENDING} with
     * its {@code next_attempt_at} pushed into the future by the backoff —
     * indistinguishable, on those two columns alone, from a scheduled reminder.
     * So a withdrawal that selected only on status and due time would kill a
     * message the student is still owed, and a booking cancelled while its
     * approval notification was backing off would silently never deliver it.
     * Drop {@code and event.dedupeKey is not null} from
     * {@code OutboxEventRepository.cancelScheduled} and this reddens; nothing
     * else in this class would notice.
     */
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

    /**
     * A reminder that has already gone is not resurrected as a dead letter, and
     * one another worker holds is left for the claim that holds it. Only unsent,
     * still-future rows are withdrawn.
     */
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

    // Fixtures

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

    /**
     * Sends the state-change notifications an approval leaves behind — the
     * booking, the submitted proof, the approval itself — so that what a later
     * {@code dispatchBatch()} does is attributable to the reminder alone.
     */
    private void drainDueNotifications() {
        while (dispatcher.dispatchBatch() > 0) {
            // Until nothing is due.
        }
        sender.reset();
    }

    /** Ages a row by writing its due time directly; nothing here injects a {@code Clock}. */
    private void dueAt(UUID eventId, OffsetDateTime when) {
        jdbc.update("update outbox_events set next_attempt_at = ? where id = ?", when, eventId);
    }

    /** The same, for a booking's payment deadline, so the sweep has something to take. */
    private void overdue(UUID bookingId) {
        jdbc.update("update bookings set payment_deadline_at = ? where id = ?",
                OffsetDateTime.now().minusMinutes(5), bookingId);
    }

    /**
     * A second row carrying a dedupe key that is already taken, written straight
     * at the table so nothing above the constraint can absorb it.
     */
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

    /** One trip per test, so each can choose how close to departure it sits. */
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
