package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.dto.CreateBookingRequest;
import com.auvan.api.booking.dto.CreateSeatHoldRequest;
import com.auvan.api.booking.dto.JoinWaitlistRequest;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.BookingStatus;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.PaymentProofRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.booking.service.BookingService;
import com.auvan.api.booking.service.PaymentProofService;
import com.auvan.api.booking.service.SeatHoldService;
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
import com.auvan.api.outbox.repository.OutboxEventRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OneBookingPerTripIntegrationTests extends AuthenticationTestSupport {
    @TestConfiguration
    static class FakeStorageConfiguration {
        @Bean
        @Primary
        InMemoryPaymentProofStorage inMemoryPaymentProofStorage() {
            return new InMemoryPaymentProofStorage();
        }
    }

    @Autowired
    private SeatHoldService holds;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private PaymentProofService paymentProofs;

    @Autowired
    private WaitlistService waitlistService;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private BookingRepository bookings;

    @Autowired
    private SeatClaimRepository claims;

    @Autowired
    private PaymentProofRepository proofs;

    @Autowired
    private WaitlistEntryRepository waitlist;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeys;

    @Autowired
    private OutboxEventRepository events;

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

    @BeforeEach
    void setUp() {
        clearData();
        trip = createTrip();
        student = users.save(new AppUser("Uper-trip-student", "Student")).getId();
    }

    @AfterEach
    void clearData() {
        events.deleteAll();
        proofs.deleteAll();
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

    @Test
    void aStudentBookedOnATripCannotHoldMoreSeatsOnIt() {
        paymentProofs.submit(student, book(0).getId(), jpeg());

        assertRefused(() -> hold(1), "already_booked_on_trip");
    }

    @Test
    void aSecondHoldOnABookedTripCannotBecomeASecondBooking() {
        paymentProofs.submit(student, book(0).getId(), jpeg());
        UUID secondHold = UUID.randomUUID();
        claims.save(new SeatClaim(seat(1), student, secondHold, OffsetDateTime.now().plusMinutes(30)));

        assertRefused(() -> confirm(secondHold, "key-second"), "already_booked_on_trip");
        assertThat(bookings.findByUserIdOrderByCreatedAtDesc(student)).hasSize(1);
    }

    @Test
    void aStudentBookedOnAFullTripCannotJoinItsWaitlist() {
        paymentProofs.submit(student, book(0).getId(), jpeg());
        UUID other = users.save(new AppUser("Uper-trip-other", "Other")).getId();
        claims.save(new SeatClaim(seat(1), other, UUID.randomUUID(), OffsetDateTime.now().plusHours(1)));

        assertRefused(() -> waitlistService.join(student, new JoinWaitlistRequest(trip.getId(), 1)),
                "already_booked_on_trip");
        assertThat(waitlist.count()).isZero();
    }

    @Test
    void aStudentWithoutABookingOnAFullTripCanJoinItsWaitlist() {
        UUID other = users.save(new AppUser("Uper-trip-other", "Other")).getId();
        trip.getSeats().forEach(seat -> claims.save(
                new SeatClaim(seat, other, UUID.randomUUID(), OffsetDateTime.now().plusHours(1))));

        waitlistService.join(student, new JoinWaitlistRequest(trip.getId(), 1));

        assertThat(waitlist.count()).isOne();
    }

    @Test
    void aCancelledBookingDoesNotCountAsBeingOnTheTrip() {
        bookingService.cancel(student, book(0).getId());

        assertThat(book(1).getStatus()).isEqualTo(BookingStatus.PENDING_PAYMENT);
    }

    private Booking book(int seatIndex) {
        confirm(hold(seatIndex), "key-" + seatIndex);
        return bookings.findByUserIdOrderByCreatedAtDesc(student).getFirst();
    }

    private UUID hold(int seatIndex) {
        return holds.hold(student, new CreateSeatHoldRequest(trip.getId(), List.of(seat(seatIndex).getId())))
                .holdId();
    }

    private void confirm(UUID holdId, String key) {
        bookingService.create(student, key, new CreateBookingRequest(holdId, "Somchai P.", "0812345678"));
    }

    private TripSeat seat(int index) {
        return trip.getSeats().get(index);
    }

    private static void assertRefused(ThrowingCallable call, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ResponseStatusException.class, refusal -> {
            assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(refusal.getBody().getProperties()).containsEntry("code", code);
        });
    }

    private static MockMultipartFile jpeg() {
        return new MockMultipartFile("file", "slip.jpg", "image/jpeg", "the-slip".getBytes(StandardCharsets.UTF_8));
    }

    private Trip createTrip() {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout VAN-PER-TRIP",
                List.of(new SeatLayoutSeat("A1", 1, 1), new SeatLayoutSeat("A2", 1, 2))));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-PER-TRIP", "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
    }
}
