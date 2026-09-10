package com.petconnect.api.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Origens liberadas para CORS, por ambiente. Configurado em
 * {@code petconnect.cors.allowed-origins} (ver application-*.yml).
 */
@ConfigurationProperties(prefix = "petconnect.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
