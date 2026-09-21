package com.auvan.api.booking.repository;

import com.auvan.api.booking.entity.PaymentProof;
import com.auvan.api.booking.entity.PaymentProofStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentProofRepository extends JpaRepository<PaymentProof, UUID> {
    /**
     * The booking's id alone, as a scalar, for a caller that is about to lock
     * that booking's row.
     *
     * <p>This exists instead of {@code findById(id).getBooking().getId()} for
     * one reason, and it is the trap this whole review path is built around: a
     * review decision has to be made from the booking the <em>lock</em>
     * returned. Loading the proof — and with it its booking — before the lock
     * puts both in the persistence context, and every later read hands that
     * pre-lock instance straight back, so the decision is made from a reading
     * taken before the lock was held. The guard still looks like a guard and
     * every test still passes. Projecting the id keeps no entity around, so
     * there is nothing stale to hand back.
     */
    @Query("select proof.booking.id from PaymentProof proof where proof.id = :id")
    Optional<UUID> findBookingIdById(@Param("id") UUID id);

    /**
     * The review queue, oldest first, with everything the list needs to render.
     * The three associations are {@code LAZY} and {@code optional = false}, so
     * the inner fetch joins are correct and keep the queue off an N+1.
     */
    @Query("select proof from PaymentProof proof "
            + "join fetch proof.booking booking join fetch booking.trip trip join fetch trip.route route "
            + "where proof.status = :status order by proof.createdAt asc")
    List<PaymentProof> findByStatusOrderByCreatedAt(@Param("status") PaymentProofStatus status);
}
