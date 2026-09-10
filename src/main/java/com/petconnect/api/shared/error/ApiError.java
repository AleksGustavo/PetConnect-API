package com.petconnect.api.shared.error;

import java.time.OffsetDateTime;

/**
 * Formato único de erro devolvido ao cliente. Nunca inclui stack trace nem
 * detalhes internos.
 *
 * @param timestamp momento do erro (ISO 8601)
 * @param status    código HTTP
 * @param code      código estável e legível por máquina (ex.: {@code RESOURCE_NOT_FOUND})
 * @param message   mensagem curta para exibição/depuração
 */
public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String code,
        String message
) {
    public static ApiError of(int status, String code, String message) {
        return new ApiError(OffsetDateTime.now(), status, code, message);
    }
}
