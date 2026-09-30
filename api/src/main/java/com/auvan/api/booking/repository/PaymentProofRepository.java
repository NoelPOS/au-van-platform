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
    @Query("select proof.booking.id from PaymentProof proof where proof.id = :id")
    Optional<UUID> findBookingIdById(@Param("id") UUID id);

    @Query("select proof from PaymentProof proof "
            + "join fetch proof.booking booking join fetch booking.trip trip join fetch trip.route route "
            + "where proof.status = :status "
            + "and booking.status <> com.auvan.api.booking.entity.BookingStatus.CANCELLED "
            + "order by proof.createdAt asc")
    List<PaymentProof> findByStatusOrderByCreatedAt(@Param("status") PaymentProofStatus status);
}
