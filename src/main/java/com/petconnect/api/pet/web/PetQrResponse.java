package com.petconnect.api.pet.web;

import com.petconnect.api.pet.domain.Pet;

import java.time.Instant;

/** QR atual de um pet, só para o tutor dono (RF16/RF19). */
public record PetQrResponse(String publicId, Instant rotatedAt) {

    public static PetQrResponse from(Pet p) {
        return new PetQrResponse(p.getPublicId(), p.getPublicIdRotatedAt());
    }
}
