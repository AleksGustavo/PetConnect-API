package com.petconnect.api.notification.web;

import com.petconnect.api.notification.application.NotificationSettingsService;
import com.petconnect.api.notification.domain.PetNotificationPreferences;
import com.petconnect.api.shared.security.AuthenticatedUser;
import com.petconnect.api.shared.web.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Avisos do QR para o tutor autenticado (issue #36 do app): o aparelho que
 * recebe o push e o que cada pet avisa. O token vai no corpo, nunca na URL,
 * para não aparecer em log de acesso.
 */
@RestController
@Tag(name = "notifications")
public class NotificationController {

    private final NotificationSettingsService settings;

    public NotificationController(NotificationSettingsService settings) {
        this.settings = settings;
    }

    public record DeviceRequest(@NotBlank @Size(max = 4096) String token,
                                @Pattern(regexp = "android|ios|web") String platform) {
    }

    public record DeviceRemoval(@NotBlank @Size(max = 4096) String token) {
    }

    public record PreferencesRequest(@NotNull Boolean notifyOnAccess, @NotNull Boolean notifyOnSighting) {
    }

    public record PreferencesResponse(boolean notifyOnAccess, boolean notifyOnSighting) {

        static PreferencesResponse from(PetNotificationPreferences p) {
            return new PreferencesResponse(p.isNotifyOnAccess(), p.isNotifyOnSighting());
        }
    }

    @PutMapping(ApiV1.BASE + "/me/push-devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Registra o aparelho para receber os avisos do QR")
    public void register(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody DeviceRequest body) {
        settings.registerDevice(me, body.token(), body.platform());
    }

    @DeleteMapping(ApiV1.BASE + "/me/push-devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Para de enviar avisos a este aparelho (ao sair da conta)")
    public void unregister(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody DeviceRemoval body) {
        settings.unregisterDevice(me, body.token());
    }

    @GetMapping(ApiV1.BASE + "/legacy-pets/{firestoreId}/notification-preferences")
    @Operation(summary = "Avisos do QR de um pet do Firestore")
    public PreferencesResponse getForFirestorePet(@AuthenticationPrincipal AuthenticatedUser me,
                                                  @PathVariable String firestoreId) {
        return PreferencesResponse.from(settings.forFirestorePet(me, firestoreId));
    }

    @PutMapping(ApiV1.BASE + "/legacy-pets/{firestoreId}/notification-preferences")
    @Operation(summary = "Liga ou desliga os avisos do QR de um pet do Firestore")
    public PreferencesResponse putForFirestorePet(@AuthenticationPrincipal AuthenticatedUser me,
                                                  @PathVariable String firestoreId,
                                                  @Valid @RequestBody PreferencesRequest body) {
        return PreferencesResponse.from(settings.updateForFirestorePet(me, firestoreId,
                body.notifyOnAccess(), body.notifyOnSighting()));
    }

    @GetMapping(ApiV1.BASE + "/pets/{id}/notification-preferences")
    @Operation(summary = "Avisos do QR de um pet")
    public PreferencesResponse getForApiPet(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable String id) {
        return PreferencesResponse.from(settings.forApiPet(me, id));
    }

    @PutMapping(ApiV1.BASE + "/pets/{id}/notification-preferences")
    @Operation(summary = "Liga ou desliga os avisos do QR de um pet")
    public PreferencesResponse putForApiPet(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable String id,
                                            @Valid @RequestBody PreferencesRequest body) {
        return PreferencesResponse.from(settings.updateForApiPet(me, id,
                body.notifyOnAccess(), body.notifyOnSighting()));
    }
}
