package com.petconnect.api.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do Firebase Admin SDK.
 *
 * @param serviceAccount caminho para o JSON da conta de serviço OU o próprio
 *                       JSON codificado em base64. Vazio = SDK não inicializa
 *                       (rotas protegidas respondem 401).
 * @param projectId      id do projeto Firebase (usado para validar o {@code aud} do token).
 */
@ConfigurationProperties(prefix = "petconnect.firebase")
public record FirebaseProperties(String serviceAccount, String projectId) {

    public boolean hasCredentials() {
        return serviceAccount != null && !serviceAccount.isBlank();
    }
}
