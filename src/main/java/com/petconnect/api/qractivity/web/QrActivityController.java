package com.petconnect.api.qractivity.web;

import com.petconnect.api.qractivity.application.QrActivityService;
import com.petconnect.api.shared.security.AuthenticatedUser;
import com.petconnect.api.shared.web.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Atividade do QR para o tutor autenticado (issue #37 do app). Pet de outro
 * tutor responde 404, como nas outras rotas do pet.
 */
@RestController
@Tag(name = "qr-activity")
public class QrActivityController {

    private final QrActivityService activity;

    public QrActivityController(QrActivityService activity) {
        this.activity = activity;
    }

    @GetMapping(ApiV1.BASE + "/legacy-pets/{firestoreId}/qr-activity")
    @Operation(summary = "Avistamentos e acessos do QR de um pet do Firestore")
    public QrActivityResponse forFirestorePet(@AuthenticationPrincipal AuthenticatedUser me,
                                              @PathVariable String firestoreId) {
        return QrActivityResponse.from(activity.forFirestorePet(me, firestoreId));
    }

    @GetMapping(ApiV1.BASE + "/pets/{id}/qr-activity")
    @Operation(summary = "Avistamentos e acessos do QR de um pet")
    public QrActivityResponse forApiPet(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable String id) {
        return QrActivityResponse.from(activity.forApiPet(me, id));
    }
}
