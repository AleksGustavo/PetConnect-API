package com.petconnect.api.upload.application;

/** Chamada de fato ao Cloudinary — isolado para poder ser mockado em teste. */
public interface CloudinaryClient {

    /**
     * Apaga um asset. Idempotente: {@code true} tanto se apagou quanto se já
     * não existia ("not found" do Cloudinary não é erro).
     */
    boolean destroy(String resourceType, String publicId);
}
