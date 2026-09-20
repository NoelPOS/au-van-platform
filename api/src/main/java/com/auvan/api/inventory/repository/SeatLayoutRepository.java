package com.auvan.api.inventory.repository;

import com.auvan.api.inventory.entity.SeatLayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SeatLayoutRepository extends JpaRepository<SeatLayout, UUID> {
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
