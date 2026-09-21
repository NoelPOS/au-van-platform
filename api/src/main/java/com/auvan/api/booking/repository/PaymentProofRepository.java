package com.auvan.api.booking.repository;

import com.auvan.api.booking.entity.PaymentProof;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentProofRepository extends JpaRepository<PaymentProof, UUID> { }
