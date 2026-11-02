package com.petconnect.api.sharing.web;

import com.petconnect.api.sharing.application.PublicSharingService.SharingChange;
import com.petconnect.api.sharing.domain.PublicField;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Corpo do {@code PUT .../public-sharing}. {@code enabled: false} desativa a
 * página e revoga o consentimento; com {@code true}, o consentimento precisa
 * vir marcado na versão atual do texto.
 */
public record PublicSharingRequest(
        @NotNull Boolean enabled,
        Boolean consentAccepted,
        @Size(max = 20) String consentVersion,
        @Size(max = 7) Set<PublicField> fields,
        @Size(max = 200) String tutorDisplayName,
        @Size(max = 30) String contactPhone
) {

    SharingChange toChange() {
        return new SharingChange(enabled, Boolean.TRUE.equals(consentAccepted), consentVersion, fields,
                tutorDisplayName, contactPhone);
    }
}
