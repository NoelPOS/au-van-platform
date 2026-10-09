package com.auvan.api.booking;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.booking.entity.Booking;
import com.auvan.api.booking.entity.SeatClaim;
import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.PaymentProofRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.service.BookingService;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mockingDetails;

// Not @Transactional: a test transaction puts every thread on one connection and hides the race.
@SpringBootTest
@Import(PaymentProofConcurrencyTestSupport.FakeStorageConfiguration.class)
abstract class PaymentProofConcurrencyTestSupport extends AuthenticationTestSupport {
    @TestConfiguration
    static class FakeStorageConfiguration {
        @Bean
        @Primary
        InMemoryPaymentProofStorage inMemoryPaymentProofStorage() {
            return new InMemoryPaymentProofStorage();
        }
    }

    @Autowired
    PaymentProofService paymentProofs;

    @Autowired
    PaymentProofReviewService review;

    @Autowired
    BookingService bookingService;

    @Autowired
    InMemoryPaymentProofStorage storage;

    @MockitoSpyBean
    BookingRepository bookings;

    @Autowired
    PaymentProofRepository proofs;

    @Autowired
    SeatClaimRepository claims;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private TripRepository trips;

    @Autowired
    private VehicleRepository vehicles;

    @Autowired
    private SeatLayoutRepository seatLayouts;

    @Autowired
    private VanRouteRepository routes;

    UUID student;
    UUID administrator;
    UUID bookingId;

    @BeforeEach
    void setUp() {
        clearData();
        storage.reset();
        Trip trip = createTrip();
        student = users.save(new AppUser("Ustudent", "Student")).getId();
        administrator = users.save(new AppUser("Uadmin", "Administrator")).getId();
        Booking booking = new Booking(trip, student, "AUV-250101-PROOFRAC", "Somchai P.",
                "0812345678", new BigDecimal("35.00"), OffsetDateTime.now().plusHours(2),
                OffsetDateTime.now());
        TripSeat seat = trip.getSeats().getFirst();
        booking.addSeat(seat);
        bookingId = bookings.save(booking).getId();
        SeatClaim claim = new SeatClaim(seat, student, UUID.randomUUID(), OffsetDateTime.now().plusMinutes(10));
        claim.attachTo(bookingId);
        claims.save(claim);
    }

    @AfterEach
    void clearData() {
        proofs.deleteAll();
        claims.deleteAll();
        bookings.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    static Object real(InvocationOnMock invocation) throws Throwable {
        return mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
    }

    static Object codeOf(ResponseStatusException problem) {
        return problem.getBody().getProperties().get("code");
    }

    static MockMultipartFile jpeg(String content) {
        return new MockMultipartFile("file", "slip.jpg", "image/jpeg", content.getBytes(StandardCharsets.UTF_8));
    }

    private Trip createTrip() {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> layoutSeats = new ArrayList<>();
        for (int column = 1; column <= 4; column++) {
            layoutSeats.add(new SeatLayoutSeat("A" + column, 1, column));
        }
        SeatLayout layout = seatLayouts.save(new SeatLayout("Contended layout", layoutSeats));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-PROOF", "Toyota Commuter", layout));
        return trips.save(new Trip(route, vehicle, OffsetDateTime.now().plusDays(1)));
    }
}
