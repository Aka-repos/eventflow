package com.eventflow.modules.identity.domain;

import com.eventflow.modules.identity.domain.exception.AccountBlockedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private User registeredUser() {
        return User.register("ana@mail.com", "$2a$hash", "Ana P.", null, Role.of(4, RoleCode.ATTENDEE));
    }

    @Test
    void should_register_active_attendee_with_uuid() {
        // When
        User user = registeredUser();
        // Then
        assertThat(user.getId()).isNotNull();
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.roleCodes()).containsExactly("ATTENDEE");
        assertThat(user.getEmail()).isEqualTo("ana@mail.com");
    }

    @Test
    void should_allow_authentication_when_active() {
        assertThatCode(registeredUser()::ensureCanAuthenticate).doesNotThrowAnyException();
    }

    @Test
    void should_reject_authentication_when_blocked() {
        // Given
        User user = registeredUser();
        user.block();
        // When / Then
        assertThatThrownBy(user::ensureCanAuthenticate).isInstanceOf(AccountBlockedException.class);
    }
}
