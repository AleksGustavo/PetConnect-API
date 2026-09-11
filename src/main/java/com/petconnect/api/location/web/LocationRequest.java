package com.petconnect.api.location.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Corpo do {@code POST /api/v1/pets/{petId}/locations} (RF31/32 — tutor
 * registra um avistamento manualmente). {@code reportedAt} é opcional — a
 * tela permite escolher uma data passada; se vier nulo, o servidor usa o
 * momento do registro.
 */
public record LocationRequest(
        LocalDate reportedAt,
        @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @Size(max = 1000) String description,
        @Size(max = 120) String reporterContact
) {
}
