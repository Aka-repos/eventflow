package com.eventflow.modules.identity.infrastructure.persistence;

import com.eventflow.modules.identity.domain.Role;
import com.eventflow.modules.identity.domain.RoleCode;
import com.eventflow.modules.identity.domain.port.RoleRepository;
import org.springframework.stereotype.Component;

@Component
class JpaRoleRepositoryAdapter implements RoleRepository {

    private final SpringDataRoleRepository jpa;

    JpaRoleRepositoryAdapter(SpringDataRoleRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Role getByCode(RoleCode code) {
        return jpa.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Rol no sembrado: " + code + " (migración V9)"));
    }
}
