package com.auvan.api.auth.repository;

import com.auvan.api.auth.entity.AppUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByLineSubject(String lineSubject);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select appUser from AppUser appUser where appUser.id = :id")
    Optional<AppUser> lockById(@Param("id") UUID id);
}
