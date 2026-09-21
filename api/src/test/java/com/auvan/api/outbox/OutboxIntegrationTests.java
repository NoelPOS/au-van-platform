package com.auvan.api.outbox;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.InMemoryPaymentProofStorage;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.PaymentProofRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.service.BookingService;
import com.auvan.api.booking.service.IdempotencyService;
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
import com.auvan.api.notification.client.LinePushMessage;
import com.auvan.api.notification.dto.BookingNotification;
import com.auvan.api.outbox.entity.OutboxEvent;
import com.auvan.api.outbox.entity.OutboxEventType;
import com.auvan.api.outbox.entity.OutboxStatus;
import com.auvan.api.outbox.repository.OutboxEventRepository;
import com.auvan.api.outbox.service.OutboxDispatcher;
import com.auvan.api.outbox.service.OutboxRecorder;
import com.auvan.api.outbox.service.OutboxScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;

/**
 * The outbox end to end: what a committed transaction leaves behind, what a
 * rolled-back one does not, and what the dispatcher then does with it.
 *
 * <p>Not {@code @Transactional}. A test-managed transaction would make the
 * rollback test meaningless — the whole point is that a real commit boundary
 * decides whether the row exists — and the dispatcher must run outside any
 * transaction of its own.
 *
 * <p>Rows are aged with {@link JdbcTemplate} rather than by waiting. Nothing in
 * this codebase injects a {@code Clock}, so a backoff of thirty seconds is
 * thirty real seconds; moving {@code next_attempt_at} instead is what makes the
 * retry, lease and dead-letter paths testable at all.
 */
@SpringBootTest
class OutboxIntegrationTests extends AuthenticationTestSupport {
    /** Both ports' test implementations, registered the way the payment-proof tests already do. */
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
    private ApplicationContext context;

    @Autowired
    private OutboxRecorder recorder;

    @Autowired
    private OutboxDispatcher dispatcher;

    @Autowired
    private OutboxEventRepository events;

    @Autowired
    private RecordingLineMessageSender sender;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PaymentProofService paymentProofs;

    @Autowired
    private PaymentProofReviewService review;

    @Autowired
    private InMemoryPaymentProofStorage storage;

    // A spy, not a mock: every call runs for real except the one a test stubs.
    @MockitoSpyBean
    private IdempotencyService idempotency;

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

    private Trip trip;
    private UUID student;
    private UUID administrator;

    @BeforeEach
    void setUp() {
        clearData();
        sender.reset();
        storage.reset();
        trip = createTrip();
        student = users.save(new AppUser("Ustudent-outbox", "Student")).getId();
        administrator = users.save(new AppUser("Uadmin-outbox", "Administrator")).getId();
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

    // Recording: the row and the state change are one commit

    @Test
    void aCommittedBookingCreationLeavesExactlyOnePendingOutboxRowForTheStudent() {
        UUID bookingId = createBooking("key-created");

        assertThat(events.findAll()).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo(OutboxEventType.BOOKING_CREATED);
            assertThat(event.getAggregateId()).isEqualTo(bookingId);
            assertThat(event.getRecipientUserId()).isEqualTo(student);
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getAttempts()).isZero();
            assertThat(event.getProcessedAt()).isNull();
            // Due immediately: a notification is owed the moment it commits.
            assertThat(event.getNextAttemptAt()).isBeforeOrEqualTo(OffsetDateTime.now());
            assertThat(event.getPayload()).contains(bookings.findById(bookingId).orElseThrow().getReference());
        });
    }

    /**
     * Acceptance criterion 1, in the only form that proves it: the write fails
     * <em>after</em> the outbox row has been recorded, and the row goes with it.
     *
     * <p>Give {@link OutboxRecorder} a transaction of its own — a
     * {@code REQUIRES_NEW} propagation — and this is the test that reddens.
     * Everything else in this class would still pass.
     */
    @Test
    void aBookingWriteThatFailsAfterRecordingLeavesNoOutboxRowAndNoBooking() {
        UUID holdId = holdOn(trip.getSeats().getFirst());
        doThrow(new IllegalStateException("The idempotency record could not be written."))
                .when(idempotency).record(any(), any(), any(), any(), anyInt(), any(), any());

        assertThatThrownBy(() -> bookingService.create(student, "key-doomed",
                new CreateBookingRequest(holdId, "Somchai P.", "0812345678")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(events.count()).isZero();
        assertThat(bookings.count()).isZero();
    }

    /**
     * The cancellation path's own trap. {@code seat_claims.deleteByBookingId}
     * is {@code @Modifying(clearAutomatically = true)}, so an outbox insert
     * that has not been flushed by the time it runs is discarded in silence.
     * Move the {@code outbox.record} call in {@code BookingService.cancel}
     * below the {@code bookings.flush()} and this test reddens with no error
     * anywhere — the row simply is not there.
     */
    @Test
    void cancellingABookingLeavesItsOutboxRowDespiteTheClaimDeleteClearingThePersistenceContext() {
        UUID bookingId = createBooking("key-cancel");

        assertThat(bookingService.cancel(student, bookingId).status()).isEqualTo(BookingStatus.CANCELLED);

        assertThat(claims.findByBookingId(bookingId)).isEmpty();
        assertThat(eventsOfType(OutboxEventType.BOOKING_CANCELLED)).singleElement().satisfies(event -> {
            assertThat(event.getAggregateId()).isEqualTo(bookingId);
            assertThat(event.getRecipientUserId()).isEqualTo(student);
        });
    }

    /**
     * The two payment transactions, and the one thing about them that is easy
     * to get wrong: an administrator's decision is news for the <em>student</em>,
     * so the recipient is the booking's owner and never the actor.
     */
    @Test
    void aSubmissionAndTheDecisionOnItEachRecordOneRowAddressedToTheStudent() {
        UUID bookingId = createBooking("key-proof");

        paymentProofs.submit(student, bookingId, jpeg("the-slip"));
        UUID proofId = proofs.findAll().getFirst().getId();
        review.approve(administrator, proofId, "Received in full.");

        assertThat(eventsOfType(OutboxEventType.PAYMENT_PROOF_SUBMITTED)).singleElement()
                .satisfies(event -> assertThat(event.getRecipientUserId()).isEqualTo(student));
        assertThat(eventsOfType(OutboxEventType.PAYMENT_APPROVED)).singleElement().satisfies(event -> {
            assertThat(event.getRecipientUserId()).isEqualTo(student);
            assertThat(event.getPayload()).contains("Received in full.");
        });
    }

    @Test
    void aRejectionRecordsItsOwnRowCarryingTheReasonTheStudentHasToAct() {
        UUID bookingId = createBooking("key-reject");
        paymentProofs.submit(student, bookingId, jpeg("the-slip"));
        UUID proofId = proofs.findAll().getFirst().getId();

        review.reject(administrator, proofId, "The slip is unreadable.");

        assertThat(eventsOfType(OutboxEventType.PAYMENT_REJECTED)).singleElement().satisfies(event -> {
            assertThat(event.getRecipientUserId()).isEqualTo(student);
            assertThat(event.getPayload()).contains("The slip is unreadable.");
        });
    }

    // Dispatch: the success path

    @Test
    void aDueRowIsSentOnceAndLandsSent() {
        UUID eventId = record("AUV-250101-DISPATCH");

        assertThat(dispatcher.dispatchBatch()).isOne();

        assertThat(sender.messages()).singleElement().satisfies(message -> {
            assertThat(message.to()).isEqualTo("Ustudent-outbox");
            assertThat(message.text()).contains("AUV-250101-DISPATCH");
            assertThat(message.retryKey()).isEqualTo(eventId.toString());
        });
        assertThat(events.findById(eventId).orElseThrow()).satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
            assertThat(event.getAttempts()).isOne();
            assertThat(event.getProcessedAt()).isNotNull();
            assertThat(event.getLastError()).isNull();
        });
    }

    @Test
    void aRowThatIsNotYetDueIsLeftAlone() {
        UUID eventId = record("AUV-250101-FUTURE");
        dueAt(eventId, OffsetDateTime.now().plusHours(1));

        assertThat(dispatcher.dispatchBatch()).isZero();

        assertThat(sender.messages()).isEmpty();
        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    /**
     * The terminal-state guard. A {@code SENT} row that somehow becomes due
     * again — a clock skew, an operator's hand on the table — must not produce
     * a second message. Drop {@code status} from the claim's {@code WHERE} and
     * this reddens.
     */
    @Test
    void aSentRowIsNeverSentAgainEvenWhenItIsDue() {
        UUID eventId = record("AUV-250101-ALREADY");
        dispatcher.dispatchBatch();
        sender.reset();
        dueAt(eventId, OffsetDateTime.now().minusMinutes(1));

        assertThat(dispatcher.dispatchBatch()).isZero();

        assertThat(sender.messages()).isEmpty();
        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    /**
     * The same guard one layer down, where it actually has to hold. The
     * candidate query filters terminal rows out, so the test above never
     * reaches the claim; this one calls the claim directly, which is what
     * happens for real when a rival resolves the row between another worker's
     * candidate read and its claim. Drop {@code status} from the claim's
     * {@code WHERE} and this reddens while everything else stays green.
     */
    @Test
    void theClaimItselfRefusesARowThatHasAlreadyBeenResolved() {
        UUID sentId = record("AUV-250101-CLAIMSENT");
        UUID deadId = record("AUV-250101-CLAIMDEAD");
        dispatcher.dispatchBatch();
        OffsetDateTime now = OffsetDateTime.now();
        events.claim(deadId, now, now);
        events.markDead(deadId, now, "spent");
        dueAt(sentId, now.minusMinutes(1));
        dueAt(deadId, now.minusMinutes(1));

        assertThat(events.claim(sentId, now, now.plusMinutes(2))).isZero();
        assertThat(events.claim(deadId, now, now.plusMinutes(2))).isZero();
    }

    /**
     * An outcome belongs to whoever holds the claim. Writing one for a row
     * nobody has claimed would let a stale worker resolve a row that has since
     * gone back to the queue. Drop {@code status} from the {@code markSent} and
     * {@code markDead} guards and this reddens.
     */
    @Test
    void anOutcomeIsRefusedForARowThatNoWorkerHasClaimed() {
        UUID eventId = record("AUV-250101-UNCLAIMED");
        OffsetDateTime now = OffsetDateTime.now();

        assertThat(events.markSent(eventId, now)).isZero();
        assertThat(events.markDead(eventId, now, "never claimed")).isZero();

        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    /**
     * The lease, which is this design's whole answer to a worker dying
     * mid-send: no sweeper collects the row, its lease simply runs out and it
     * becomes due again.
     */
    @Test
    void aClaimedRowIsUntouchableUntilItsLeaseRunsOutAndIsThenReclaimed() {
        UUID eventId = record("AUV-250101-LEASED");
        OffsetDateTime now = OffsetDateTime.now();
        assertThat(events.claim(eventId, now, now.plusMinutes(2))).isOne();

        assertThat(dispatcher.dispatchBatch()).isZero();
        assertThat(sender.messages()).isEmpty();
        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.IN_FLIGHT);

        dueAt(eventId, OffsetDateTime.now().minusSeconds(1));

        assertThat(dispatcher.dispatchBatch()).isOne();
        assertThat(sender.messages()).hasSize(1);
        assertThat(events.findById(eventId).orElseThrow()).satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.SENT);
            // The dead worker's attempt was spent, so a send that always kills
            // its worker still runs out of attempts rather than looping.
            assertThat(event.getAttempts()).isEqualTo(2);
        });
    }

    // Dispatch: the failure path

    @Test
    void aSendThatThrowsLeavesTheRowPendingWithOneSpentAttemptAndABackedOffDeadline() {
        UUID eventId = record("AUV-250101-FAILS");
        sender.failNext(1);

        assertThat(dispatcher.dispatchBatch()).isZero();

        assertThat(events.findById(eventId).orElseThrow()).satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
            assertThat(event.getAttempts()).isOne();
            assertThat(event.getLastError()).contains("LINE did not accept the push.");
            assertThat(event.getProcessedAt()).isNull();
            // The first backoff is outbox.backoff-base, thirty seconds.
            assertThat(event.getNextAttemptAt()).isAfter(OffsetDateTime.now().plusSeconds(20));
        });
        // Not claimable again until then: the row is out of the batch entirely.
        assertThat(dispatcher.dispatchBatch()).isZero();
    }

    /**
     * Acceptance criterion 2 as a test, and the one that would otherwise be
     * quietly missing. A send that reached LINE and then failed is
     * indistinguishable from one that never arrived, so the retry must carry
     * the <em>same</em> retry key — LINE deduplicates on it, and without that
     * the student reads the message twice. Nothing else in this suite would
     * notice if the key were rebuilt per attempt.
     */
    @Test
    void aRetryCarriesTheSameRetryKeyAsTheSendThatFailed() {
        UUID eventId = record("AUV-250101-RETRYKEY");
        sender.failNext(1);

        dispatcher.dispatchBatch();
        dueAt(eventId, OffsetDateTime.now().minusSeconds(1));
        assertThat(dispatcher.dispatchBatch()).isOne();

        assertThat(sender.messages()).hasSize(2);
        assertThat(sender.messages()).extracting(LinePushMessage::retryKey).containsOnly(eventId.toString());
        assertThat(events.findById(eventId).orElseThrow().getStatus()).isEqualTo(OutboxStatus.SENT);
    }

    /**
     * Acceptance criterion 4: the attempt budget is spent, the row lands
     * somewhere an operator can find it, and nothing retries it again however
     * due it looks.
     */
    @Test
    void aRowThatExhaustsItsAttemptsGoesDeadAndStopsBeingRetried() {
        UUID eventId = record("AUV-250101-DOOMED");
        sender.failNext(10);

        for (int attempt = 1; attempt <= 5; attempt++) {
            dueAt(eventId, OffsetDateTime.now().minusSeconds(1));
            dispatcher.dispatchBatch();
        }

        assertThat(events.findById(eventId).orElseThrow()).satisfies(event -> {
            assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD);
            // outbox.max-attempts, and not one more.
            assertThat(event.getAttempts()).isEqualTo(5);
            assertThat(event.getLastError()).contains("LINE did not accept the push.");
            assertThat(event.getProcessedAt()).isNotNull();
        });
        assertThat(sender.messages()).hasSize(5);

        dueAt(eventId, OffsetDateTime.now().minusSeconds(1));
        assertThat(dispatcher.dispatchBatch()).isZero();
        assertThat(sender.messages()).hasSize(5);
    }

    // The scheduling gate

    /**
     * The gate, from the side that matters to everyone else's tests: under the
     * test configuration there is no scheduler bean at all, so no background
     * sweep can move a fixture under a concurrency assertion. Remove
     * {@code @ConditionalOnProperty} from {@code OutboxScheduler} and this
     * reddens. {@code OutboxSchedulingIntegrationTests} proves the other side —
     * that turning the property on really does dispatch.
     */
    @Test
    void noSchedulerRunsUnderTheTestConfiguration() {
        assertThat(context.getBeanNamesForType(OutboxScheduler.class)).isEmpty();
    }

    // Fixtures

    /** A booking made the way a student makes one, so the create path really runs. */
    private UUID createBooking(String idempotencyKey) {
        UUID holdId = holdOn(trip.getSeats().getFirst());
        bookingService.create(student, idempotencyKey, new CreateBookingRequest(holdId, "Somchai P.", "0812345678"));
        return bookings.findAll().getFirst().getId();
    }

    private UUID holdOn(TripSeat seat) {
        UUID holdId = UUID.randomUUID();
        claims.save(new SeatClaim(seat, student, holdId, OffsetDateTime.now().plus(Duration.ofMinutes(10))));
        return holdId;
    }

    /** One outbox row with no booking behind it, for the dispatch paths. */
    private UUID record(String reference) {
        return recorder.record(OutboxEventType.BOOKING_CREATED, UUID.randomUUID(), student,
                new BookingNotification(reference, "Booked seats A1."), OffsetDateTime.now()).getId();
    }

    /**
     * Ages a row by writing {@code next_attempt_at} directly. Nothing injects a
     * {@code Clock}, so the alternative is waiting out a real backoff.
     */
    private void dueAt(UUID eventId, OffsetDateTime when) {
        jdbc.update("update outbox_events set next_attempt_at = ? where id = ?", when, eventId);
    }

    private List<OutboxEvent> eventsOfType(OutboxEventType type) {
        return events.findAll().stream().filter(event -> event.getEventType() == type).toList();
    }

    private static MockMultipartFile jpeg(String content) {
        return new MockMultipartFile("file", "slip.jpg", "image/jpeg", content.getBytes(StandardCharsets.UTF_8));
    }

    private Trip createTrip() {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 4; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Outbox layout", layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-OUTBOX", "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
    }
}
