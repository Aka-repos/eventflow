package com.eventflow.modules.identity.application.command;

public record RegisterUserCommand(String email, String password, String fullName, String phone) {
}
