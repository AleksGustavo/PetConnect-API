package com.petconnect.api.location.web;

import com.petconnect.api.pet.domain.Pet;

/**
 * O que a página pública do QR pode ver — nunca inclui {@code tutorId},
 * e-mail ou qualquer dado do tutor. Endpoint sem autenticação.
 */
public record PublicPetSummaryResponse(
        String publicId,
        String name,
        String species,
        String status,
        String photoUrl,
        String publicContactPhone
) {

    public static PublicPetSummaryResponse from(Pet p) {
        return new PublicPetSummaryResponse(
                p.getPublicId(),
                p.getName(),
                p.getSpecies() == null ? null : p.getSpecies().name(),
                p.getStatus() == null ? null : p.getStatus().name(),
                p.getPhotoUrl(),
                p.getPublicContactPhone());
    }
}
