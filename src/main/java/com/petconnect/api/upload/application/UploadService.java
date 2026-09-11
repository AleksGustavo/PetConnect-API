package com.petconnect.api.upload.application;

import com.petconnect.api.shared.config.CloudinaryProperties;
import com.petconnect.api.shared.error.ApiException;
import com.petconnect.api.upload.web.UploadSignatureResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Upload/exclusão assinados no Cloudinary (FASE 10) — troca o preset
 * unsigned (upload sem controle de exclusão) por requisições assinadas com
 * a API secret, que só existe aqui no servidor.
 */
@Service
public class UploadService {

    private final CloudinaryProperties props;
    private final CloudinaryClient client;

    public UploadService(CloudinaryProperties props, CloudinaryClient client) {
        this.props = props;
        this.client = client;
    }

    public UploadSignatureResponse sign() {
        requireConfigured();
        long timestamp = Instant.now().getEpochSecond();
        String signature = CloudinarySigner.sign(Map.of("timestamp", String.valueOf(timestamp)), props.apiSecret());
        return new UploadSignatureResponse(signature, timestamp, props.apiKey(), props.cloudName());
    }

    /**
     * Idempotente: não lança se o arquivo já não existir no Cloudinary. Uma
     * falha de rede/upstream é logada e engolida — mesma postura tolerante
     * que o app já tinha para exclusão de anexo (nunca bloqueou a operação
     * principal por causa disso).
     */
    public void deleteByUrl(String url) {
        requireConfigured();
        CloudinaryUrlParser.Parsed parsed = CloudinaryUrlParser.parse(url);
        client.destroy(parsed.resourceType(), parsed.publicId());
    }

    private void requireConfigured() {
        if (!props.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "UPLOAD_NOT_CONFIGURED",
                    "Upload assinado não configurado no servidor.");
        }
    }
}
