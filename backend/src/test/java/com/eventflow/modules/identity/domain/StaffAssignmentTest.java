package com.eventflow.modules.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Asignación de staff de acceso: permisos, revocación y verificación para check-in. */
class StaffAssignmentTest {

    @Test
    void assign_is_active_with_default_permission() {
        StaffAssignment a = StaffAssignment.assign(UUID.randomUUID(), UUID.randomUUID(),
                List.of("CHECKIN_EVENT"), UUID.randomUUID());
        assertThat(a.isActive()).isTrue();
        assertThat(a.canCheckInEvent()).isTrue();
        assertThat(a.getId().version()).isEqualTo(7);
    }

    @Test
    void revoke_makes_inactive() {
        StaffAssignment a = StaffAssignment.assign(UUID.randomUUID(), UUID.randomUUID(),
                List.of("CHECKIN_EVENT"), UUID.randomUUID());
        a.revoke(Instant.now());
        assertThat(a.isActive()).isFalse();
        assertThat(a.canCheckInEvent()).isFalse();
    }

    @Test
    void without_checkin_permission_cannot_scan() {
        StaffAssignment a = StaffAssignment.assign(UUID.randomUUID(), UUID.randomUUID(),
                List.of("OTHER_PERMISSION"), UUID.randomUUID());
        assertThat(a.canCheckInEvent()).isFalse();
    }

    @Test
    void empty_permissions_defaults_to_checkin() {
        StaffAssignment a = StaffAssignment.assign(UUID.randomUUID(), UUID.randomUUID(),
                List.of(), UUID.randomUUID());
        assertThat(a.canCheckInEvent()).isTrue();
    }
}
