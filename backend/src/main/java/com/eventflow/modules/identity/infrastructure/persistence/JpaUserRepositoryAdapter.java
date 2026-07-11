package com.eventflow.modules.identity.infrastructure.persistence;

import com.eventflow.modules.identity.domain.User;
import com.eventflow.modules.identity.domain.exception.EmailAlreadyRegisteredException;
import com.eventflow.modules.identity.domain.port.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
class JpaUserRepositoryAdapter implements UserRepository {

    private final SpringDataUserRepository jpa;

    JpaUserRepositoryAdapter(SpringDataUserRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpa.findByEmail(email);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpa.existsByEmail(email);
    }

    /**
     * saveAndFlush + traducción: si la carrera pierde contra el índice único parcial
     * (uq_users_email_alive, case-insensitive por CITEXT), la violación se traduce al
     * error de dominio — jamás un 500 (engineering/02 §7 y §9).
     */
    @Override
    public User save(User user) {
        try {
            return jpa.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyRegisteredException();
        }
    }
}
