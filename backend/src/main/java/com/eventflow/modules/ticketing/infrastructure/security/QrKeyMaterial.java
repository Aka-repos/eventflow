package com.eventflow.modules.ticketing.infrastructure.security;

import java.security.KeyPair;

/** Material de firma del QR (kid + par EC P-256), provisto por configuración. */
public record QrKeyMaterial(String keyId, KeyPair keyPair) {
}
