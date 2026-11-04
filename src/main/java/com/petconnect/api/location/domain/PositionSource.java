package com.petconnect.api.location.domain;

/**
 * De onde veio a coordenada de um relato da página do QR (issue #37 do app).
 * Nenhuma das duas é prova de onde o pet está: o servidor não confere a
 * posição que o navegador informa.
 */
public enum PositionSource {
    /** Quem relatou permitiu a localização do navegador e confirmou o ponto. */
    BROWSER_GEOLOCATION,
    /** Sem coordenada: só o texto de quem relatou, não verificado. */
    NONE
}
