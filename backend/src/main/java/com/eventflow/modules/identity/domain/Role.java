package com.eventflow.modules.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "roles", schema = "identity")
public class Role {

    @Id
    private Short id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true)
    private RoleCode code;

    protected Role() {
    }

    private Role(Short id, RoleCode code) {
        this.id = id;
        this.code = code;
    }

    public static Role of(int id, RoleCode code) {
        return new Role((short) id, code);
    }

    public Short getId() {
        return id;
    }

    public RoleCode getCode() {
        return code;
    }
}
