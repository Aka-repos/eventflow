package com.eventflow.modules.identity.application.support;

/** Puerto de generación de refresh tokens opacos (adapter con SecureRandom en infrastructure). */
public interface TokenGenerator {

    String generate();
}
