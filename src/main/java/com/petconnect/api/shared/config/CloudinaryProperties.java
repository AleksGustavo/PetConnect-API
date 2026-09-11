package com.petconnect.api.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciais do Cloudinary para upload/exclusão **assinados** (FASE 10).
 *
 * <p>{@code cloudName} não é segredo (já está em {@code cloudinary_config.dart}
 * no app). {@code apiKey}/{@code apiSecret} são segredos de verdade — só
 * existem aqui via env var, nunca no app nem no git.
 */
@ConfigurationProperties(prefix = "petconnect.cloudinary")
public record CloudinaryProperties(String cloudName, String apiKey, String apiSecret) {

    public boolean isConfigured() {
        return hasText(apiKey) && hasText(apiSecret) && hasText(cloudName);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
