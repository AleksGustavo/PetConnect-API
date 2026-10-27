package com.petconnect.api.pet.web;

import com.petconnect.api.pet.application.PetQrService;
import com.petconnect.api.shared.security.AuthenticatedUser;
import com.petconnect.api.shared.web.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * QR code do pet para o tutor dono (RF16/RF19): consultar o atual e
 * regenerar. Rotas {@code /legacy-pets/{firestoreId}} atendem o app em modo
 * Firestore (o padrão), que só conhece o id do documento no Firestore.
 */
@RestController
@Tag(name = "pets")
public class PetQrController {

    private final PetQrService qr;

    public PetQrController(PetQrService qr) {
        this.qr = qr;
    }

    @GetMapping(ApiV1.BASE + "/pets/{id}/qr")
    @Operation(summary = "QR code atual de um pet do tutor")
    public PetQrResponse current(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable String id) {
        return PetQrResponse.from(qr.current(me.userId(), id));
    }

    @PostMapping(ApiV1.BASE + "/pets/{id}/qr/rotation")
    @Operation(summary = "Regenera o QR code; o anterior deixa de funcionar")
    public PetQrResponse rotate(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable String id,
                                @Valid @RequestBody RotateQrRequest body) {
        return PetQrResponse.from(qr.rotate(me.userId(), id, body.expectedPublicId()));
    }

    @GetMapping(ApiV1.BASE + "/legacy-pets/{firestoreId}/qr")
    @Operation(summary = "QR code atual de um pet do app em modo Firestore")
    public PetQrResponse currentByLegacyId(@AuthenticationPrincipal AuthenticatedUser me,
                                           @PathVariable String firestoreId) {
        return PetQrResponse.from(qr.currentByLegacyId(me.userId(), firestoreId));
    }

    @PostMapping(ApiV1.BASE + "/legacy-pets/{firestoreId}/qr/rotation")
    @Operation(summary = "Regenera o QR code de um pet do app em modo Firestore")
    public PetQrResponse rotateByLegacyId(@AuthenticationPrincipal AuthenticatedUser me,
                                          @PathVariable String firestoreId,
                                          @Valid @RequestBody RotateQrRequest body) {
        return PetQrResponse.from(qr.rotateByLegacyId(me.userId(), firestoreId, body.expectedPublicId()));
    }
}
