package com.petconnect.api.medicalrecord.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * Corpo do {@code POST} e do {@code PATCH} de histórico médico.
 *
 * @param id campo só usado no {@code POST}: os anexos (Cloudinary) sobem
 *           usando um id gerado no app antes de o registro existir, então o
 *           cliente escolhe o id em vez do servidor gerar um. Ignorado no
 *           {@code PATCH} (o id vem da URL).
 */
public record MedicalRecordRequest(
        String id,
        @NotNull LocalDate recordedAt,
        @NotBlank @Size(max = 2000) String description,
        @Size(max = 120) String veterinarian,
        List<String> attachments
) {
}
