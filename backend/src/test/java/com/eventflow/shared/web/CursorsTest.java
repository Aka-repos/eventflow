package com.eventflow.shared.web;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cursor opaco keyset (api/07): roundtrip, dirección y rechazo de basura. */
class CursorsTest {

    @Test
    void roundtrip_preserves_key() {
        UUID id = UUID.randomUUID();
        String cursor = Cursors.encode(1735689600000L, id, true);
        Cursors.Key key = Cursors.decode(cursor, true);
        assertThat(key.sortMillis()).isEqualTo(1735689600000L);
        assertThat(key.id()).isEqualTo(id);
        assertThat(key.descending()).isTrue();
    }

    @Test
    void direction_mismatch_is_malformed_request() {
        String cursor = Cursors.encode(1000L, UUID.randomUUID(), false);
        assertThatThrownBy(() -> Cursors.decode(cursor, true))
                .isInstanceOf(Cursors.MalformedCursorException.class);
    }

    @Test
    void garbage_cursor_is_malformed_request() {
        assertThatThrownBy(() -> Cursors.decode("no-es-un-cursor", false))
                .isInstanceOf(Cursors.MalformedCursorException.class);
        assertThatThrownBy(() -> Cursors.decode("YWJjZA", false))
                .isInstanceOf(Cursors.MalformedCursorException.class);
    }
}
