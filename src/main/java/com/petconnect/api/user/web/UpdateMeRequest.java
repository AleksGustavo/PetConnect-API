package com.petconnect.api.user.web;

import com.petconnect.api.user.application.ProfileUpdate;
import com.petconnect.api.user.domain.Gender;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Corpo do {@code PATCH /api/v1/me}. Todos os campos são opcionais:
 * {@code null} não altera; string vazia limpa.
 */
public record UpdateMeRequest(
        @Size(max = 80) String firstName,
        @Size(max = 80) String lastName,
        @Size(max = 30) String phone,
        @Past LocalDate birthDate,
        Gender gender,
        @Size(max = 2048) String photoUrl
) {

    public ProfileUpdate toProfileUpdate() {
        return new ProfileUpdate(firstName, lastName, phone, birthDate, gender, photoUrl);
    }
}
