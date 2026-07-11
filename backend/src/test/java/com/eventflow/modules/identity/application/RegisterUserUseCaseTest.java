package com.eventflow.modules.identity.application;

import com.eventflow.modules.identity.application.command.RegisterUserCommand;
import com.eventflow.modules.identity.application.result.AuthResult;
import com.eventflow.modules.identity.domain.Role;
import com.eventflow.modules.identity.domain.RoleCode;
import com.eventflow.modules.identity.domain.User;
import com.eventflow.modules.identity.domain.UserStatus;
import com.eventflow.modules.identity.domain.exception.EmailAlreadyRegisteredException;
import com.eventflow.modules.identity.domain.port.RoleRepository;
import com.eventflow.modules.identity.domain.port.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegisterUserUseCaseTest {

    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthTokenIssuer tokenIssuer;
    @InjectMocks RegisterUserUseCase useCase;

    private final RegisterUserCommand command =
            new RegisterUserCommand("ana@mail.com", "S3gura!pass", "Ana P.", null);

    @Test
    void should_register_attendee_and_issue_tokens_when_email_is_free() {
        // Given
        when(userRepository.existsByEmail("ana@mail.com")).thenReturn(false);
        when(roleRepository.getByCode(RoleCode.ATTENDEE)).thenReturn(Role.of(4, RoleCode.ATTENDEE));
        when(passwordEncoder.encode("S3gura!pass")).thenReturn("$2a$encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        AuthResult expected = new AuthResult("access", 900, "refresh-plain", UUID.randomUUID(), null);
        when(tokenIssuer.issueFor(any(User.class))).thenReturn(expected);

        // When
        AuthResult result = useCase.execute(command);

        // Then
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("ana@mail.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("$2a$encoded");
        assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(saved.getValue().roleCodes()).containsExactly("ATTENDEE");
        assertThat(result.accessToken()).isEqualTo("access");
    }

    @Test
    void should_reject_when_email_already_registered() {
        // Given
        when(userRepository.existsByEmail("ana@mail.com")).thenReturn(true);

        // When / Then
        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        verify(userRepository, never()).save(any());
    }
}
