package com.eventflow.shared.security;

import java.util.Set;
import java.util.UUID;

/** Principal autenticado extraído del access token. */
public record AuthenticatedUser(UUID id, String email, Set<String> roles) {
}
