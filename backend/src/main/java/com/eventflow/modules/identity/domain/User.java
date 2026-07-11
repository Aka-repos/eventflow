package com.eventflow.modules.identity.domain;

import com.eventflow.modules.identity.domain.exception.AccountBlockedException;
import com.eventflow.shared.domain.Uuids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Agregado User (identity). El estado solo cambia por métodos de negocio (doc engineering/06 §6.11).
 * Soft Delete (ADR-16): los repositorios filtran deleted_at IS NULL.
 */
@Entity
@Table(name = "users", schema = "identity")
@SQLRestriction("deleted_at IS NULL")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", schema = "identity",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected User() {
    }

    private User(UUID id, String email, String passwordHash, String fullName, String phone, Role role) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.phone = phone;
        this.status = UserStatus.ACTIVE;
        this.roles.add(role);
    }

    public static User register(String email, String passwordHash, String fullName, String phone, Role attendeeRole) {
        return new User(Uuids.v7(), email, passwordHash, fullName, phone, attendeeRole);
    }

    /** Regla: solo cuentas ACTIVE pueden autenticarse. */
    public void ensureCanAuthenticate() {
        if (status == UserStatus.BLOCKED) {
            throw new AccountBlockedException();
        }
    }

    public void block() {
        this.status = UserStatus.BLOCKED;
    }

    public Set<String> roleCodes() {
        return Set.copyOf(roles.stream().map(r -> r.getCode().name()).collect(java.util.stream.Collectors.toSet()));
    }

    public List<String> sortedRoleCodes() {
        return roleCodes().stream().sorted().toList();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
