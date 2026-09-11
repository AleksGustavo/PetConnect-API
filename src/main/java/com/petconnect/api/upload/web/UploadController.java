package com.petconnect.api.upload.web;

import com.petconnect.api.shared.security.AuthenticatedUser;
import com.petconnect.api.shared.web.ApiV1;
import com.petconnect.api.upload.application.UploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Upload/exclusão assinados no Cloudinary (FASE 10). Autenticado — a posse
 * do arquivo é verificada via a pasta assinada no momento do upload (ver
 * {@code UploadService}), não por uma tabela própria.
 */
@RestController
@RequestMapping(ApiV1.BASE + "/uploads")
@Tag(name = "uploads")
public class UploadController {

    private final UploadService uploads;

    public UploadController(UploadService uploads) {
        this.uploads = uploads;
    }

    @PostMapping("/signature")
    @Operation(summary = "Assina uma requisição de upload direto para o Cloudinary")
    public UploadSignatureResponse signature(@AuthenticationPrincipal AuthenticatedUser me) {
        return uploads.sign(me.userId());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui um arquivo do Cloudinary pela URL, se pertencer ao tutor autenticado (idempotente)")
    public void delete(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody DeleteUploadRequest body) {
        uploads.deleteByUrl(me.userId(), body.url());
    }
}
