package com.eventflow.modules.identity.infrastructure.persistence;

import com.eventflow.modules.identity.domain.Role;
import com.eventflow.modules.identity.domain.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface SpringDataRoleRepository extends JpaRepository<Role, Short> {

    Optional<Role> findByCode(RoleCode code);
}
