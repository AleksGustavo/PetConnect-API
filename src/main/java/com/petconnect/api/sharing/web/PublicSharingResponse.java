package com.petconnect.api.sharing.web;

import com.petconnect.api.sharing.application.PublicSharingService;
import com.petconnect.api.sharing.domain.PublicField;
import com.petconnect.api.sharing.domain.PublicSharing;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Configuração da página pública como o tutor vê no app. {@code active} diz
 * se a página está mostrando os dados agora; {@code currentConsentVersion} é
 * a versão do texto que a API aceita.
 */
public record PublicSharingResponse(
        boolean enabled,
        boolean active,
        Set<PublicField> fields,
        String tutorDisplayName,
        String contactPhone,
        List<String> galleryPhotoIds,
        Instant consentAcceptedAt,
        String consentVersion,
        Instant consentRevokedAt,
        Instant updatedAt,
        String currentConsentVersion
) {

    public static PublicSharingResponse from(PublicSharing s) {
        return new PublicSharingResponse(
                s.isEnabled(),
                s.isActive(PublicSharingService.CONSENT_VERSION),
                s.getFields(),
                s.getTutorDisplayName(),
                s.getContactPhoneE164(),
                s.getGalleryPhotoIds(),
                s.getConsentAcceptedAt(),
                s.getConsentVersion(),
                s.getConsentRevokedAt(),
                s.getUpdatedAt(),
                PublicSharingService.CONSENT_VERSION);
    }
}
