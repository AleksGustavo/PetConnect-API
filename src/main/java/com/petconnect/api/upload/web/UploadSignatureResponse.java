package com.petconnect.api.upload.web;

/** O app usa isto para fazer um upload **assinado** direto pro Cloudinary. */
public record UploadSignatureResponse(String signature, long timestamp, String apiKey, String cloudName) {
}
