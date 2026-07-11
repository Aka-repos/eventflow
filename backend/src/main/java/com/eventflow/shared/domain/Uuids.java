package com.eventflow.shared.domain;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generación de UUID v7 (ordenado en el tiempo) en la aplicación — decisión del diseño de BD
 * (db/07-bd-07 §3.2): mejor localidad de B-tree que v4; PG17 no trae uuidv7() nativo.
 */
public final class Uuids {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Uuids() {
    }

    public static UUID v7() {
        long millis = System.currentTimeMillis();
        byte[] rnd = new byte[10];
        RANDOM.nextBytes(rnd);

        long msb = (millis & 0xFFFFFFFFFFFFL) << 16;              // 48 bits de timestamp
        msb |= 0x7000L;                                            // versión 7
        msb |= ((rnd[0] & 0x0FL) << 8) | (rnd[1] & 0xFFL);         // 12 bits aleatorios

        long lsb = 0x8000000000000000L;                            // variante RFC 4122
        for (int i = 2; i < 10; i++) {
            lsb |= (rnd[i] & 0xFFL) << (8 * (9 - i));
        }
        lsb &= 0xBFFFFFFFFFFFFFFFL;
        lsb |= 0x8000000000000000L;

        return new UUID(msb, lsb);
    }
}
