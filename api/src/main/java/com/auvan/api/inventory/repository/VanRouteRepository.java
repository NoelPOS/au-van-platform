package com.auvan.api.inventory.repository;

import com.auvan.api.inventory.entity.VanRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VanRouteRepository extends JpaRepository<VanRoute, UUID> { }
