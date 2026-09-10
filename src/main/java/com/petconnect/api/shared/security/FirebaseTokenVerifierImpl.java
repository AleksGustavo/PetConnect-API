package com.petconnect.api.shared.security;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Implementação real: delega ao Firebase Admin SDK. Se o SDK não foi inicializado
 * (sem credencial), toda verificação falha com {@link TokenVerificationException}.
 */
@Component
public class FirebaseTokenVerifierImpl implements FirebaseTokenVerifier {

    private final ObjectProvider<FirebaseAuth> firebaseAuth;

    public FirebaseTokenVerifierImpl(ObjectProvider<FirebaseAuth> firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    @Override
    public VerifiedToken verify(String idToken) {
        FirebaseAuth auth = firebaseAuth.getIfAvailable();
        if (auth == null) {
            throw new TokenVerificationException("Firebase Admin SDK não configurado no servidor.");
        }
        try {
            FirebaseToken token = auth.verifyIdToken(idToken, true);
            return new VerifiedToken(
                    token.getUid(),
                    token.getEmail(),
                    token.getName(),
                    token.getPicture());
        } catch (FirebaseAuthException e) {
            throw new TokenVerificationException("Token inválido ou expirado.", e);
        }
    }
}
