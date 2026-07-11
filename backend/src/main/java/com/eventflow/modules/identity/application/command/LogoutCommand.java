package com.eventflow.modules.identity.application.command;

import java.util.UUID;

public record LogoutCommand(UUID userId, String refreshToken) {
}
