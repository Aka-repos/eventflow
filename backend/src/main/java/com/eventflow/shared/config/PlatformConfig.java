package com.eventflow.shared.config;

/** Puerto de lectura de ops.global_config (doc 10: permitido para todos los módulos). */
public interface PlatformConfig {

    /** Lee value->{field} numérico de la clave, con fallback si no existe. */
    int intValue(String key, String field, int fallback);
}
