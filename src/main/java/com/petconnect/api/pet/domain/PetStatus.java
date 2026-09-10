package com.petconnect.api.pet.domain;

/** Situação do pet. Novo campo — não existia no Firestore. */
public enum PetStatus {
    ACTIVE,
    LOST,
    FOUND,
    DECEASED,
    ARCHIVED
}
