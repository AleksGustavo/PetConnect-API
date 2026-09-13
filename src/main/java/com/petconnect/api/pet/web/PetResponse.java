package com.petconnect.api.pet.web;

import com.petconnect.api.pet.domain.Pet;

import java.time.Instant;
import java.time.LocalDate;

/** Representação pública de um pet. */
public record PetResponse(
        String id,
        String publicId,
        String name,
        String species,
        String breed,
        String color,
        String gender,
        String size,
        Double weightKg,
        LocalDate birthDate,
        String status,
        Boolean vaccinatedFlag,
        String publicContactPhone,
        String photoUrl,
        String coverPhotoUrl,
        Instant createdAt,
        Instant updatedAt
) {

    public static PetResponse from(Pet p) {
        return new PetResponse(
                p.getId(),
                p.getPublicId(),
                p.getName(),
                p.getSpecies() == null ? null : p.getSpecies().name(),
                p.getBreed(),
                p.getColor(),
                p.getGender() == null ? null : p.getGender().name(),
                p.getSize() == null ? null : p.getSize().name(),
                p.getWeightKg(),
                p.getBirthDate(),
                p.getStatus() == null ? null : p.getStatus().name(),
                p.getVaccinatedFlag(),
                p.getPublicContactPhone(),
                p.getPhotoUrl(),
                p.getCoverPhotoUrl(),
                p.getCreatedAt(),
                p.getUpdatedAt());
    }
}
