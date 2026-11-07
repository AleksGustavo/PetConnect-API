package com.petconnect.api.privacy.web;

import com.petconnect.api.privacy.application.QrDataErasureService;
import com.petconnect.api.shared.security.AuthenticatedUser;
import com.petconnect.api.shared.web.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exclusão, a pedido do tutor, do que a API guarda de um pet do app em modo
 * Firestore (LGPD). O app chama antes de apagar o pet no Firestore.
 */
@RestController
@Tag(name = "pets")
public class QrDataErasureController {

    private final QrDataErasureService erasure;

    public QrDataErasureController(QrDataErasureService erasure) {
        this.erasure = erasure;
    }

    @DeleteMapping(ApiV1.BASE + "/legacy-pets/{firestoreId}/qr-data")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Apaga página pública, acessos, relatos, preferências e avisos do QR de um pet do Firestore",
            description = "Também apaga a cópia migrada do pet no MongoDB, se for do mesmo tutor. Idempotente.")
    public void eraseByLegacyId(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable String firestoreId) {
        erasure.eraseForFirestorePet(me, firestoreId);
    }
}
