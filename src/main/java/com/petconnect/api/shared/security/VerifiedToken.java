package com.petconnect.api.shared.security;

/**
 * Dados extraídos de um Firebase ID Token já verificado.
 *
 * @param uid     identificador do usuário no Firebase Auth ({@code sub}/{@code user_id})
 * @param email   e-mail associado (pode ser {@code null})
 * @param name    nome de exibição (pode ser {@code null})
 * @param picture URL da foto de perfil no provedor (pode ser {@code null})
 */
public record VerifiedToken(String uid, String email, String name, String picture) {
}
