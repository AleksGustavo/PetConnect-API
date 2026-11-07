package com.petconnect.api.location.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.petconnect.api.sharing.application.PublicSharingService.PublicPetView;

import java.util.List;

/**
 * O que a página pública do QR recebe. Só os campos que o tutor marcou e
 * consentiu (issue #35 do app); os outros nem aparecem no JSON. Sem
 * consentimento, só {@code shared: false}.
 *
 * <p>Nunca inclui id interno, {@code tutorId}, e-mail, situação ou dado
 * clínico. Os nomes {@code name}, {@code species}, {@code photoUrl} e
 * {@code publicContactPhone} são os que a página já usava.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicPetSummaryResponse(
        boolean shared,
        String name,
        String species,
        String breed,
        String color,
        String photoUrl,
        /** Carrossel da fase 2 (perfil, foto 2, foto 3); ausente quando não está completo. */
        List<String> photos,
        String tutorName,
        String publicContactPhone
) {

    public static PublicPetSummaryResponse from(PublicPetView v) {
        return new PublicPetSummaryResponse(v.shared(), v.name(), v.species(), v.breed(), v.color(),
                v.photoUrl(), v.photos(), v.tutorName(), v.contactPhone());
    }
}
