package com.eventflow.modules.identity.application;

import com.eventflow.modules.identity.domain.port.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** ÚNICA superficie de identity para otros módulos (doc 10, S¹: resolución de usuarios). */
@Component
public class IdentityFacade {

    private final UserRepository userRepository;

    public IdentityFacade(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public String userDisplayName(UUID userId) {
        return userRepository.findById(userId).map(u -> u.getFullName()).orElse("");
    }
}
