package com.petconnect.api.shared.security;

/**
 * Verifica um Firebase ID Token. Abstração sobre o Firebase Admin SDK — permite
 * testar o fluxo de autenticação sem credencial real.
 */
public interface FirebaseTokenVerifier {

    /**
     * @param idToken o valor cru do header {@code Authorization: Bearer <idToken>}
     * @return dados do token verificado
     * @throws TokenVerificationException se o token for inválido/expirado, ou se o
     *                                   SDK não estiver configurado
     */
    VerifiedToken verify(String idToken) throws TokenVerificationException;
}
