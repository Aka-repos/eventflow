package com.eventflow.modules.identity.application;

import com.eventflow.modules.identity.application.command.LoginCommand;
import com.eventflow.modules.identity.application.result.AuthResult;
import com.eventflow.modules.identity.domain.Role;
import com.eventflow.modules.identity.domain.RoleCode;
import com.eventflow.modules.identity.domain.User;
import com.eventflow.modules.identity.domain.exception.AccountBlockedException;
import com.eventflow.modules.identity.domain.exception.InvalidCredentialsException;
import com.eventflow.modules.identity.domain.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginUseCaseTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthTokenIssuer tokenIssuer;
    @InjectMocks LoginUseCase useCase;

    private User user() {
        return User.register("ana@mail.com", "$2a$hash", "Ana P.", null, Role.of(4, RoleCode.ATTENDEE));
    }

    @Test
    void should_issue_tokens_when_credentials_are_valid() {
        // Given
        when(userRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("S3gura!pass", "$2a$hash")).thenReturn(true);
        AuthResult expected = new AuthResult("access", 900, "refresh", UUID.randomUUID(), null);
        when(tokenIssuer.issueFor(any(User.class))).thenReturn(expected);

        // When
        AuthResult result = useCase.execute(new LoginCommand("ana@mail.com", "S3gura!pass"));

        // Then
        assertThat(result.accessToken()).isEqualTo("access");
    }

    @Test
    void should_reject_unknown_email_with_generic_credentials_error() {
        // Given: no revelar si el email existe (anti-enumeración)
        when(userRepository.findByEmail("nadie@mail.com")).thenReturn(Optional.empty());

        // When / Then
        assertThatThrownBy(() -> useCase.execute(new LoginCommand("nadie@mail.com", "x")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void should_reject_wrong_password() {
        when(userRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("mala", "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(new LoginCommand("ana@mail.com", "mala")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void should_reject_blocked_account_after_password_check() {
        User blocked = user();
        blocked.block();
        when(userRepository.findByEmail("ana@mail.com")).thenReturn(Optional.of(blocked));
        when(passwordEncoder.matches("S3gura!pass", "$2a$hash")).thenReturn(true);

        assertThatThrownBy(() -> useCase.execute(new LoginCommand("ana@mail.com", "S3gura!pass")))
                .isInstanceOf(AccountBlockedException.class);
    }
}
