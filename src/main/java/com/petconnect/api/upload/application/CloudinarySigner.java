package com.petconnect.api.upload.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Assinatura de requisições ao Cloudinary: SHA-1 dos parâmetros (exceto
 * {@code file}, {@code cloud_name}, {@code resource_type}, {@code api_key})
 * em ordem alfabética, concatenados com {@code api_secret} no fim — sem
 * separador antes dele. Ver
 * https://cloudinary.com/documentation/authentication_signatures.
 */
public final class CloudinarySigner {

    private CloudinarySigner() {
    }

    public static String sign(Map<String, String> params, String apiSecret) {
        String toSign = new TreeMap<>(params).entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));
        return sha1Hex(toSign + apiSecret);
    }

    private static String sha1Hex(String raw) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] digest = sha1.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-1 é garantido pela JVM (java.security.Security) — não deveria acontecer.
            throw new IllegalStateException("SHA-1 indisponível na JVM.", e);
        }
    }
}
