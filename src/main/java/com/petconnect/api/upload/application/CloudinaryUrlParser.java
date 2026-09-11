package com.petconnect.api.upload.application;

import com.petconnect.api.shared.error.ApiException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai {@code resourceType} e {@code publicId} de uma URL do Cloudinary,
 * necessários para assinar um {@code destroy}. Só reconhece o formato que o
 * próprio app produz (sem transformações): .../{resourceType}/upload/v{n}/{publicId}.{ext}
 */
public final class CloudinaryUrlParser {

    private CloudinaryUrlParser() {
    }

    private static final Pattern PATTERN = Pattern.compile(
            "res\\.cloudinary\\.com/[^/]+/(image|video|raw)/upload/v\\d+/(.+)$");

    public record Parsed(String resourceType, String publicId) {
    }

    public static Parsed parse(String url) {
        if (url == null) {
            throw ApiException.badRequest("URL do Cloudinary ausente.");
        }
        Matcher m = PATTERN.matcher(url);
        if (!m.find()) {
            throw ApiException.badRequest("URL do Cloudinary em formato inesperado.");
        }
        String resourceType = m.group(1);
        String rest = m.group(2);
        int lastSlash = rest.lastIndexOf('/');
        int dot = rest.lastIndexOf('.');
        String publicId = dot > lastSlash ? rest.substring(0, dot) : rest;
        return new Parsed(resourceType, publicId);
    }
}
