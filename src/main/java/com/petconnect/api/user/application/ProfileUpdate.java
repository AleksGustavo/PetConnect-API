package com.petconnect.api.user.application;

import com.petconnect.api.user.domain.Gender;

import java.time.LocalDate;

/**
 * Campos editáveis do perfil do tutor. {@code null} = não alterar aquele campo.
 * String vazia = limpar o campo.
 */
public record ProfileUpdate(
        String firstName,
        String lastName,
        String phone,
        LocalDate birthDate,
        Gender gender,
        String photoUrl
) {
}
