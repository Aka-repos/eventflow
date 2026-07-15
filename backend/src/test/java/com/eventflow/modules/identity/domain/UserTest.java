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

    // ===== updateProfile (PUT /me): solo fullName y phone son editables =====

    @Test
    void update_profile_changes_name_and_phone() {
        User user = registeredUser();
        user.updateProfile("Ana María Vega", "+50762222222");
        assertThat(user.getFullName()).isEqualTo("Ana María Vega");
        assertThat(user.getPhone()).isEqualTo("+50762222222");
    }

    @Test
    void update_profile_trims_name_and_clears_blank_phone() {
        User user = registeredUser();
        user.updateProfile("  Ana  ", "   ");
        assertThat(user.getFullName()).isEqualTo("Ana");
        assertThat(user.getPhone()).isNull();
    }

    @Test
    void update_profile_allows_null_phone() {
        User user = registeredUser();
        user.updateProfile("Ana", null);
        assertThat(user.getPhone()).isNull();
    }

    @Test
    void update_profile_rejects_blank_name() {
        User user = registeredUser();
        assertThatThrownBy(() -> user.updateProfile("  ", "+50762222222"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_profile_rejects_name_over_200_chars() {
        User user = registeredUser();
        assertThatThrownBy(() -> user.updateProfile("x".repeat(201), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_profile_does_not_touch_email_nor_roles() {
        User user = registeredUser();
        user.updateProfile("Nuevo Nombre", "+50763333333");
        assertThat(user.getEmail()).isEqualTo("ana@mail.com");
        assertThat(user.roleCodes()).containsExactly("ATTENDEE");
    }
}
