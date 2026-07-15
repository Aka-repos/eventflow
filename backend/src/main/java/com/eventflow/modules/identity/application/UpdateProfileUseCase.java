package com.eventflow.modules.identity.application;

import com.eventflow.modules.identity.application.command.UpdateProfileCommand;
import com.eventflow.modules.identity.domain.User;
import com.eventflow.modules.identity.domain.exception.UserNotFoundException;
import com.eventflow.modules.identity.domain.port.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PUT /me (updateProfile): el usuario autenticado actualiza sus datos editables (fullName, phone).
 * El id llega del token, no del cuerpo — imposible editar el perfil de otro. Optimistic lock (@Version).
 */
@Service
public class UpdateProfileUseCase {

    private final UserRepository userRepository;

    public UpdateProfileUseCase(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User execute(UpdateProfileCommand command) {
        User user = userRepository.findById(command.userId())
                .orElseThrow(UserNotFoundException::new);
        user.updateProfile(command.fullName(), command.phone());
        return userRepository.save(user);
    }
}
