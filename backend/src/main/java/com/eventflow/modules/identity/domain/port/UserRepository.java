package com.eventflow.modules.identity.domain.port;

import com.eventflow.modules.identity.domain.User;

import java.util.Optional;
import java.util.UUID;

/** Puerto de dominio; adapter JPA en infrastructure (hexagonal). */
public interface UserRepository {

    Optional<User> findByEmail(String email);

    Optional<User> findById(UUID id);

    boolean existsByEmail(String email);

    User save(User user);
}
