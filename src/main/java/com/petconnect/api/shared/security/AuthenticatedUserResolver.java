package com.petconnect.api.shared.security;

/**
 * Ponte entre a autenticação (módulo {@code shared}) e o módulo {@code user}:
 * dado um token verificado, garante que exista o documento em {@code users}
 * (provisiona no primeiro acesso) e devolve o principal.
 */
public interface AuthenticatedUserResolver {

    AuthenticatedUser resolve(VerifiedToken token);
}
