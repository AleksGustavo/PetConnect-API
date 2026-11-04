package com.petconnect.api.location.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo do {@code POST /api/v1/public/pets/{publicId}/sightings}: relato
 * anônimo de quem escaneou o QR e viu o pet (RF31, issue #37 do app). Sem
 * autenticação. Precisa de uma coordenada ou de um texto; a coordenada vem
 * com latitude e longitude juntas e, de preferência, com a precisão do
 * navegador.
 *
 * <p>{@code clientRequestId} é gerado pela página a cada envio: repetir o
 * mesmo envio (rede instável, toque duplo) não cria outro relato.
 */
public record SightingRequest(
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @DecimalMin("0.0") @DecimalMax("100000.0") Double accuracyMeters,
        @Size(max = 1000) String description,
        @Size(max = 120) String reporterContact,
        @Pattern(regexp = "[A-Za-z0-9-]{8,64}") String clientRequestId
) {

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }
}
