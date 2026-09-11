package com.petconnect.api.medicalrecord.web;

import com.petconnect.api.medicalrecord.domain.MedicalRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Representação pública de um registro de histórico médico. */
public record MedicalRecordResponse(
        String id,
        String petId,
        LocalDate recordedAt,
        String description,
        String veterinarian,
        List<String> attachments,
        String origin,
        Instant createdAt,
        Instant updatedAt
) {

    public static MedicalRecordResponse from(MedicalRecord r) {
        return new MedicalRecordResponse(
                r.getId(),
                r.getPetId(),
                r.getRecordedAt(),
                r.getDescription(),
                r.getVeterinarian(),
                r.getAttachments(),
                r.getOrigin() == null ? null : r.getOrigin().name(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
