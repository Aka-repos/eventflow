package com.eventflow.modules.identity.application;

import com.eventflow.modules.identity.application.command.RegisterUserCommand;
import com.eventflow.modules.identity.application.result.AuthResult;
import com.eventflow.modules.identity.domain.Role;
import com.eventflow.modules.identity.domain.RoleCode;
import com.eventflow.modules.identity.domain.User;
import com.eventflow.modules.identity.domain.exception.EmailAlreadyRegisteredException;
import com.eventflow.modules.identity.domain.port.RoleRepository;
import com.eventflow.modules.identity.domain.port.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegisterUserUseCase {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenIssuer tokenIssuer;

    public RegisterUserUseCase(UserRepository userRepository, RoleRepository roleRepository,
                               PasswordEncoder passwordEncoder, AuthTokenIssuer tokenIssuer) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
    }

    @Transactional
    public AuthResult execute(RegisterUserCommand command) {
        if (userRepository.existsByEmail(normalizeEmail(command.email()))) {
            throw new EmailAlreadyRegisteredException();
        }
        Role attendee = roleRepository.getByCode(RoleCode.ATTENDEE);
        User user = User.register(normalizeEmail(command.email()), passwordEncoder.encode(command.password()),
                command.fullName(), command.phone(), attendee);
        User saved = userRepository.save(user);
        return tokenIssuer.issueFor(saved).withUser(saved);
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
