package com.petconnect.api.user.web;

import com.petconnect.api.shared.security.AuthenticatedUser;
import com.petconnect.api.shared.web.ApiV1;
import com.petconnect.api.user.application.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Perfil do tutor autenticado. */
@RestController
@RequestMapping(ApiV1.BASE + "/me")
@Tag(name = "me", description = "Perfil do usuário autenticado")
public class MeController {

    private final UserService users;

    public MeController(UserService users) {
        this.users = users;
    }

    @GetMapping
    @Operation(summary = "Retorna o perfil do usuário autenticado (provisiona no 1º acesso)")
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return UserResponse.from(users.getByFirebaseUid(principal.firebaseUid()));
    }

    @PatchMapping
    @Operation(summary = "Atualiza campos do perfil do usuário autenticado")
    public UserResponse updateMe(@AuthenticationPrincipal AuthenticatedUser principal,
                                 @Valid @RequestBody UpdateMeRequest body) {
        return UserResponse.from(users.update(principal.firebaseUid(), body.toProfileUpdate()));
    }
}
