package com.petconnect.api.shared.security;

/** Lançada quando um Firebase ID Token é inválido, expirado, ausente ou não verificável. */
public class TokenVerificationException extends RuntimeException {

    public TokenVerificationException(String message) {
        super(message);
    }

    public TokenVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
