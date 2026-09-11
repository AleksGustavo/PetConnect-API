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
 *
 * <p><b>Posse do arquivo</b> (endurecido depois da FASE 10 — não havia
 * verificação nenhuma antes): não existe uma tabela "quem subiu o quê", e
 * criar uma exigiria mudar o fluxo de upload direto pro Cloudinary. Em vez
 * disso, cada assinatura de upload já inclui um {@code folder} =
 * {@code users/<tutorId>} nos parâmetros assinados — o Cloudinary exige que
 * o cliente envie exatamente os parâmetros assinados, então o app não pode
 * subir um arquivo fora dessa pasta sem invalidar a assinatura. Na exclusão,
 * basta checar que o {@code public_id} da URL está dentro da pasta do tutor
 * autenticado. Isto cobre inclusive o caso de um anexo que o tutor subiu e
 * removeu antes de salvar o formulário (nunca chegou a ser referenciado em
 * nenhum documento do Mongo) — uma verificação baseada em "está referenciado
 * em algum registro" não cobriria esse caso.
 */
@Service
public class UploadService {

    private final CloudinaryProperties props;
    private final CloudinaryClient client;

    public UploadService(CloudinaryProperties props, CloudinaryClient client) {
        this.props = props;
        this.client = client;
    }

    public UploadSignatureResponse sign(String tutorId) {
        requireConfigured();
        long timestamp = Instant.now().getEpochSecond();
        String folder = folderFor(tutorId);
        String signature = CloudinarySigner.sign(
                Map.of("timestamp", String.valueOf(timestamp), "folder", folder),
                props.apiSecret());
        return new UploadSignatureResponse(signature, timestamp, props.apiKey(), props.cloudName(), folder);
    }

    /**
     * Idempotente **dentro da pasta do tutor**: não lança se o arquivo já
     * não existir no Cloudinary (falha de rede/upstream é logada e engolida
     * — mesma postura tolerante que o app já tinha). Fora da pasta do
     * tutor (arquivo de outro tutor, ou URL que não segue o padrão de
     * pasta esperado — ex.: upload de antes deste endurecimento), responde
     * 404 sem distinguir "não existe" de "não é seu", pelo mesmo motivo que
     * o resto da API não vaza existência de recurso de outro tutor.
     */
    public void deleteByUrl(String tutorId, String url) {
        requireConfigured();
        CloudinaryUrlParser.Parsed parsed = CloudinaryUrlParser.parse(url);
        String expectedPrefix = folderFor(tutorId) + "/";
        if (!parsed.publicId().startsWith(expectedPrefix)) {
            throw ApiException.notFound("Arquivo não encontrado.");
        }
        client.destroy(parsed.resourceType(), parsed.publicId());
    }

    private static String folderFor(String tutorId) {
        return "users/" + tutorId;
    }

    private void requireConfigured() {
        if (!props.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "UPLOAD_NOT_CONFIGURED",
                    "Upload assinado não configurado no servidor.");
        }
    }
}
