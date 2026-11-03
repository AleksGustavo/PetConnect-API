package com.petconnect.api.location.web;

import com.petconnect.api.location.application.LocationService;
import com.petconnect.api.shared.web.ApiV1;
import com.petconnect.api.sharing.application.PublicSharingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Página pública do QR Code (RF17–19) e relato anônimo de avistamento
 * (RF31). <b>Sem autenticação</b> — ver {@code SecurityConfig.PUBLIC_PATHS}.
 * Só devolve o que o tutor autorizou ({@link PublicSharingService}).
 */
@RestController
@RequestMapping(ApiV1.BASE + "/public/pets/{publicId}")
@Tag(name = "public")
public class PublicPetController {

    private final PublicSharingService sharing;
    private final LocationService locations;

    public PublicPetController(PublicSharingService sharing, LocationService locations) {
        this.sharing = sharing;
        this.locations = locations;
    }

    /** {@code no-store}: revogar a página vale no acesso seguinte, sem cópia em cache. */
    @GetMapping
    @Operation(summary = "Resumo público de um pet pelo publicId do QR (sem autenticação)")
    public ResponseEntity<PublicPetSummaryResponse> summary(@PathVariable String publicId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Robots-Tag", "noindex")
                .body(PublicPetSummaryResponse.from(sharing.publicView(publicId)));
    }

    /** Responde só 201, sem corpo: quem relata não precisa de id interno nem do eco do que enviou. */
    @PostMapping("/sightings")
    @Operation(summary = "Relata um avistamento anônimo (sem autenticação)")
    public ResponseEntity<Void> reportSighting(@PathVariable String publicId,
                                               @Valid @RequestBody SightingRequest body) {
        locations.reportSighting(sharing.resolveForSighting(publicId), body);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).build();
    }
}
