package com.petconnect.api.sharing.domain;

/**
 * Dados que o tutor pode publicar na página do QR, um a um (issue #35 do app).
 * Allowlist: o que não está aqui nunca sai no endpoint público.
 */
public enum PublicField {
    PET_NAME,
    SPECIES,
    BREED,
    COLOR,
    PROFILE_PHOTO,
    TUTOR_NAME,
    CONTACT_PHONE
}
