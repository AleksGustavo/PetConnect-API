package com.petconnect.api.upload.web;

import jakarta.validation.constraints.NotBlank;

public record DeleteUploadRequest(@NotBlank String url) {
}
