package com.petconnect.api.location.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

/**
 * Corpo do {@code POST /api/v1/public/pets/{publicId}/sightings} — relato
 * anônimo de quem escaneou o QR e viu o pet (RF31). Sem autenticação, então
 * nenhum campo é obrigatório além de existir o pet.
 */
public record SightingRequest(
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Size(max = 1000) String description,
        @Size(max = 120) String reporterContact
) {
}
