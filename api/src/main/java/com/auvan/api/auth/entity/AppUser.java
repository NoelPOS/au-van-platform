package com.auvan.api.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users")
public class AppUser {
    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "line_subject", nullable = false, unique = true)
    private String lineSubject;

    @Column(name = "display_name")
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplicationRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppUser() { }

    public AppUser(String lineSubject, String displayName) {
        this.lineSubject = lineSubject;
        this.displayName = displayName;
        this.role = ApplicationRole.STUDENT;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public String getLineSubject() { return lineSubject; }
    public String getDisplayName() { return displayName; }
    public ApplicationRole getRole() { return role; }

    public void updateDisplayName(String displayName) {
        this.displayName = displayName;
        this.updatedAt = Instant.now();
    }

    public void promoteToAdmin() {
        this.role = ApplicationRole.ADMIN;
        this.updatedAt = Instant.now();
    }
}
