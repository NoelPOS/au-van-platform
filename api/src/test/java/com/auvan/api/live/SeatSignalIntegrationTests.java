package com.auvan.api.live;

import com.auvan.api.booking.repository.BookingRepository;
import com.auvan.api.booking.repository.IdempotencyKeyRepository;
import com.auvan.api.booking.repository.SeatClaimRepository;
import com.auvan.api.booking.repository.WaitlistEntryRepository;
import com.auvan.api.booking.service.BookingExpiryWriter;
import com.auvan.api.booking.service.WaitlistPromotionWriter;
import com.auvan.api.inventory.entity.SeatLayout;
import com.auvan.api.inventory.entity.SeatLayoutSeat;
import com.auvan.api.inventory.entity.Trip;
import com.auvan.api.inventory.entity.TripStatus;
import com.auvan.api.inventory.entity.VanRoute;
import com.auvan.api.inventory.entity.Vehicle;
import com.auvan.api.inventory.repository.SeatLayoutRepository;
import com.auvan.api.inventory.repository.TripRepository;
import com.auvan.api.inventory.repository.VanRouteRepository;
import com.auvan.api.inventory.repository.VehicleRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeatSignalIntegrationTests extends LiveUpdatesTestSupport {
    @Autowired private VanRouteRepository routes;
    @Autowired private SeatLayoutRepository seatLayouts;
    @Autowired private VehicleRepository vehicles;
    @Autowired private TripRepository trips;
    @Autowired private SeatClaimRepository claims;
    @Autowired private BookingRepository bookings;
    @Autowired private IdempotencyKeyRepository idempotencyKeys;
    @Autowired private WaitlistEntryRepository waitlist;
    @Autowired private BookingExpiryWriter expiry;
    @Autowired private WaitlistPromotionWriter promotions;

    private SignedIn holder;
    private MockHttpServletResponse watcher;
    private Trip trip;

    @BeforeEach
    void setUp() throws Exception {
        clear();
        holder = student("Useat-holder");
        watcher = stream(student("Useat-watcher"));
    }

    @AfterEach
    void clear() {
        claims.deleteAll();
        waitlist.deleteAll();
        bookings.deleteAll();
        idempotencyKeys.deleteAll();
        trips.deleteAll();
        vehicles.deleteAll();
        seatLayouts.deleteAll();
        routes.deleteAll();
        users.deleteAll();
    }

    @Test
    void holdingAndReleasingSeatsTellsEveryoneWatching() throws Exception {
        trip = createTrip(2);

        String holdId = hold(holder);
        assertThat(tripSignalsSeen()).isEqualTo(1);

        perform(holder, "/api/v1/seat-holds/" + holdId + "/release", "").andExpect(status().isNoContent());
        assertThat(tripSignalsSeen()).isEqualTo(2);
    }

    @Test
    void cancellingABookingTellsEveryoneItsSeatsAreFree() throws Exception {
        trip = createTrip(2);
        String bookingId = book(holder, hold(holder));
        int before = tripSignalsSeen();

        perform(holder, "/api/v1/bookings/" + bookingId + "/cancel", "").andExpect(status().isOk());

        assertThat(tripSignalsSeen()).isEqualTo(before + 1);
    }

    @Test
    void anUnpaidBookingThatExpiresTellsEveryoneItsSeatsAreFree() throws Exception {
        trip = createTrip(2);
        UUID bookingId = UUID.fromString(book(holder, hold(holder)));
        int before = tripSignalsSeen();

        assertThat(expiry.expire(bookingId, OffsetDateTime.now().plusDays(2))).isTrue();

        assertThat(tripSignalsSeen()).isEqualTo(before + 1);
    }

    @Test
    void aWaitlistOfferAndItsWithdrawalTellEveryoneWatching() throws Exception {
        trip = createTrip(1);
        String holdId = hold(holder);
        SignedIn waiting = student("Useat-waiting");
        String entryId = JsonPath.read(perform(waiting, "/api/v1/waitlist",
                        "{\"tripId\":\"" + trip.getId() + "\",\"seatsWanted\":1}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        perform(holder, "/api/v1/seat-holds/" + holdId + "/release", "").andExpect(status().isNoContent());
        int before = tripSignalsSeen();

        assertThat(promotions.promote(UUID.fromString(entryId), OffsetDateTime.now())).isTrue();
        assertThat(tripSignalsSeen()).isEqualTo(before + 1);

        perform(waiting, "/api/v1/waitlist/" + entryId + "/leave", "").andExpect(status().isNoContent());
        assertThat(tripSignalsSeen()).isEqualTo(before + 2);
    }

    private int tripSignalsSeen() throws Exception {
        return watcher.getContentAsString().split("\"kind\":\"trip\",\"id\":\"" + trip.getId(), -1).length - 1;
    }

    private String hold(SignedIn who) throws Exception {
        String seatId = trip.getSeats().getFirst().getId().toString();
        return JsonPath.read(perform(who, "/api/v1/seat-holds",
                        "{\"tripId\":\"" + trip.getId() + "\",\"seatIds\":[\"" + seatId + "\"]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.holdId");
    }

    private String book(SignedIn who, String holdId) throws Exception {
        return JsonPath.read(mockMvc.perform(post("/api/v1/bookings")
                        .header("Authorization", "Bearer " + who.accessToken())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holdId\":\"" + holdId + "\",\"passengerName\":\"Somchai P.\","
                                + "\"passengerPhone\":\"0812345678\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private org.springframework.test.web.servlet.ResultActions perform(SignedIn who, String path, String body)
            throws Exception {
        return mockMvc.perform(post(path)
                .header("Authorization", "Bearer " + who.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private Trip createTrip(int seatCount) {
        VanRoute route = routes.save(new VanRoute("AU", "Asok", new BigDecimal("35.00"), 45));
        List<SeatLayoutSeat> seats = java.util.stream.IntStream.rangeClosed(1, seatCount)
                .mapToObj(column -> new SeatLayoutSeat("A" + column, 1, column)).toList();
        SeatLayout layout = seatLayouts.save(new SeatLayout("Layout", seats));
        Vehicle vehicle = vehicles.save(new Vehicle("VAN-LIVE", "Toyota Commuter", layout));
        OffsetDateTime departureAt = OffsetDateTime.now().plusDays(1);
        Trip created = new Trip(route, vehicle, departureAt);
        created.update(departureAt, TripStatus.ACTIVE);
        return trips.save(created);
    }
}
