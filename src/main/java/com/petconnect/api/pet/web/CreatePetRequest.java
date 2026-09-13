package com.petconnect.api.pet.web;

import com.petconnect.api.pet.domain.PetGender;
import com.petconnect.api.pet.domain.PetSize;
import com.petconnect.api.pet.domain.PetStatus;
import com.petconnect.api.pet.domain.Species;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Corpo do {@code POST /api/v1/pets}. Só {@code name} é obrigatório. */
public record CreatePetRequest(
        @NotBlank @Size(max = 80) String name,
        Species species,
        @Size(max = 80) String breed,
        @Size(max = 40) String color,
        PetGender gender,
        PetSize size,
        @Positive @DecimalMax("500.0") Double weightKg,
        LocalDate birthDate,
        PetStatus status,
        Boolean vaccinatedFlag,
        @Size(max = 30) String publicContactPhone,
        @Size(max = 2048) String photoUrl,
        @Size(max = 2048) String coverPhotoUrl
) {
}
