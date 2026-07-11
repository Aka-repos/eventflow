package com.eventflow.modules.identity.application;

import com.eventflow.modules.identity.application.command.LoginCommand;
import com.eventflow.modules.identity.application.result.AuthResult;
import com.eventflow.modules.identity.domain.User;
import com.eventflow.modules.identity.domain.exception.InvalidCredentialsException;
import com.eventflow.modules.identity.domain.port.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenIssuer tokenIssuer;

    public LoginUseCase(UserRepository userRepository, PasswordEncoder passwordEncoder,
                        AuthTokenIssuer tokenIssuer) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
    }

    @Transactional
    public AuthResult execute(LoginCommand command) {
        User user = userRepository.findByEmail(normalizeEmail(command.email()))
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        user.ensureCanAuthenticate();
        return tokenIssuer.issueFor(user).withUser(user);
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
