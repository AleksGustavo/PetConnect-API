package com.petconnect.api.shared.security;

import java.util.Set;

/**
 * Principal autenticado, disponível via {@code @AuthenticationPrincipal}.
 *
 * @param userId     id do documento em {@code users} (MongoDB)
 * @param firebaseUid uid no Firebase Auth
 * @param email      e-mail do token
 * @param roles      papéis do usuário (ex.: {@code TUTOR})
 */
public record AuthenticatedUser(String userId, String firebaseUid, String email, Set<String> roles) {
}
