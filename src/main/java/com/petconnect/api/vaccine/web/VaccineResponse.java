package com.petconnect.api.vaccine.web;

import com.petconnect.api.vaccine.domain.Vaccine;

import java.time.Instant;
import java.time.LocalDate;

/** Representação pública de um registro de vacina. */
public record VaccineResponse(
        String id,
        String petId,
        String name,
        LocalDate appliedAt,
        LocalDate nextDoseAt,
        String veterinarian,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {

    public static VaccineResponse from(Vaccine v) {
        return new VaccineResponse(
                v.getId(),
                v.getPetId(),
                v.getName(),
                v.getAppliedAt(),
                v.getNextDoseAt(),
                v.getVeterinarian(),
                v.getNotes(),
                v.getCreatedAt(),
                v.getUpdatedAt());
    }
}
