package com.petconnect.api.pet.web;

import jakarta.validation.constraints.NotBlank;

/**
 * @param expectedPublicId o {@code publicId} que o app está mostrando; se o
 *                         QR já tiver sido trocado, a API responde 409 em vez
 *                         de trocar de novo
 */
public record RotateQrRequest(@NotBlank String expectedPublicId) {
}
