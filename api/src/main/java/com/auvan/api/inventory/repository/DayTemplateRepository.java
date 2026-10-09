package com.auvan.api.inventory.repository;

import com.auvan.api.inventory.entity.DayTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DayTemplateRepository extends JpaRepository<DayTemplate, UUID> {
    List<DayTemplate> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
