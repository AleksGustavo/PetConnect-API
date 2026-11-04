package com.petconnect.api.location.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo do {@code POST /api/v1/public/pets/{publicId}/events}. Hoje só existe
 * {@code qr_page_opened}; a localização vai pelo relato de avistamento, nunca
 * por aqui.
 */
public record PageEventRequest(@NotNull Type type) {

    public enum Type {
        @JsonProperty("qr_page_opened")
        QR_PAGE_OPENED
    }
}
