package com.eventflow.modules.identity.application.command;

import java.util.UUID;

/** El userId proviene del JWT (nunca del cuerpo): el usuario solo edita su propio perfil. */
public record UpdateProfileCommand(UUID userId, String fullName, String phone) {
}
