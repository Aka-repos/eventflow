package com.eventflow.shared.web;

import com.eventflow.shared.error.ErrorCode;
import com.eventflow.shared.error.DomainException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Cursor opaco keyset (api/07): base64url de "sortKeyEpochMillis|uuid|dir".
 * Cambiar sort o filtros invalida el cursor ⇒ 400 malformed_request.
 */
public final class Cursors {

    private Cursors() {
    }

    public record Key(long sortMillis, UUID id, boolean descending) {
    }

    public static String encode(long sortMillis, UUID id, boolean descending) {
        String raw = sortMillis + "|" + id + "|" + (descending ? "d" : "a");
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** @throws MalformedCursorException si el cursor no es válido o su dirección no coincide. */
    public static Key decode(String cursor, boolean expectedDescending) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|");
            boolean descending = "d".equals(parts[2]);
            if (descending != expectedDescending) {
                throw new MalformedCursorException("El cursor fue emitido con otro orden; reinicia desde la primera página");
            }
            return new Key(Long.parseLong(parts[0]), UUID.fromString(parts[1]), descending);
        } catch (MalformedCursorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MalformedCursorException("Cursor inválido");
        }
    }

    public static class MalformedCursorException extends DomainException {

        public MalformedCursorException(String detail) {
            super(ErrorCode.MALFORMED_REQUEST, detail);
        }
    }
}
