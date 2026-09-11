package com.petconnect.api.upload.web;

/**
 * O app usa isto para fazer um upload **assinado** direto pro Cloudinary.
 *
 * <p>{@code folder} faz parte dos parâmetros assinados (não é decoração):
 * prefixa o {@code public_id} gerado pelo Cloudinary com o id do tutor
 * autenticado, e como faz parte da assinatura, o app não pode enviar um
 * valor diferente sem invalidá-la. É assim que a posse do arquivo é
 * verificada depois, na exclusão (ver {@code UploadService.deleteByUrl}) —
 * sem precisar de uma tabela própria de "quem subiu o quê".
 */
public record UploadSignatureResponse(String signature, long timestamp, String apiKey, String cloudName,
                                      String folder) {
}
