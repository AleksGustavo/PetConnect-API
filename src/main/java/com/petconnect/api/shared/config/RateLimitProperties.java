package com.petconnect.api.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites de requisição por IP para os endpoints **públicos**
 * (`/api/v1/public/**`, FASE 9) — únicos da API sem autenticação, logo os
 * únicos vulneráveis a abuso anônimo (spam de relatos, varredura de pets).
 */
@ConfigurationProperties(prefix = "petconnect.rate-limit")
public record RateLimitProperties(int publicReadPerMinute, int publicWritePerMinute) {

    public RateLimitProperties {
        if (publicReadPerMinute <= 0) {
            publicReadPerMinute = 30;
        }
        if (publicWritePerMinute <= 0) {
            publicWritePerMinute = 10;
        }
    }
}
