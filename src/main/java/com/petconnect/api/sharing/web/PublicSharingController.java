package com.petconnect.api.sharing.web;

import com.petconnect.api.shared.security.AuthenticatedUser;
import com.petconnect.api.shared.web.ApiV1;
import com.petconnect.api.sharing.application.PublicSharingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Configuração da página pública do QR pelo tutor (issue #35 do app). Pet de
 * outro tutor responde 404, como um pet inexistente.
 */
@RestController
@Tag(name = "pets")
public class PublicSharingController {

    private final PublicSharingService sharing;

    public PublicSharingController(PublicSharingService sharing) {
        this.sharing = sharing;
    }

    @GetMapping(ApiV1.BASE + "/legacy-pets/{firestoreId}/public-sharing")
    @Operation(summary = "O que a página pública mostra de um pet do app em modo Firestore")
    public PublicSharingResponse currentByLegacyId(@AuthenticationPrincipal AuthenticatedUser me,
                                                   @PathVariable String firestoreId) {
        return PublicSharingResponse.from(sharing.currentForFirestorePet(me, firestoreId));
    }

    @PutMapping(ApiV1.BASE + "/legacy-pets/{firestoreId}/public-sharing")
    @Operation(summary = "Publica, altera ou revoga a página pública de um pet do app em modo Firestore")
    public PublicSharingResponse updateByLegacyId(@AuthenticationPrincipal AuthenticatedUser me,
                                                  @PathVariable String firestoreId,
                                                  @Valid @RequestBody PublicSharingRequest body) {
        return PublicSharingResponse.from(sharing.updateForFirestorePet(me, firestoreId, body.toChange()));
    }

    @GetMapping(ApiV1.BASE + "/pets/{id}/public-sharing")
    @Operation(summary = "O que a página pública mostra de um pet da API")
    public PublicSharingResponse current(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable String id) {
        return PublicSharingResponse.from(sharing.currentForApiPet(me, id));
    }

    @PutMapping(ApiV1.BASE + "/pets/{id}/public-sharing")
    @Operation(summary = "Publica, altera ou revoga a página pública de um pet da API")
    public PublicSharingResponse update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable String id,
                                        @Valid @RequestBody PublicSharingRequest body) {
        return PublicSharingResponse.from(sharing.updateForApiPet(me, id, body.toChange()));
    }
}
