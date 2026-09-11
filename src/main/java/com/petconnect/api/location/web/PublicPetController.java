package com.petconnect.api.location.web;

import com.petconnect.api.location.application.LocationService;
import com.petconnect.api.shared.web.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Página pública do QR Code (RF17–19) e relato anônimo de avistamento
 * (RF31). <b>Sem autenticação</b> — ver {@code SecurityConfig.PUBLIC_PATHS}.
 * Nunca expõe dado do tutor (id, e-mail, telefone pessoal).
 */
@RestController
@RequestMapping(ApiV1.BASE + "/public/pets/{publicId}")
@Tag(name = "public")
public class PublicPetController {

    private final LocationService locations;

    public PublicPetController(LocationService locations) {
        this.locations = locations;
    }

    @GetMapping
    @Operation(summary = "Resumo público de um pet pelo publicId do QR (sem autenticação)")
    public PublicPetSummaryResponse summary(@PathVariable String publicId) {
        return PublicPetSummaryResponse.from(locations.publicSummary(publicId));
    }

    @PostMapping("/sightings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Relata um avistamento anônimo (sem autenticação)")
    public LocationResponse reportSighting(@PathVariable String publicId,
                                           @Valid @RequestBody SightingRequest body) {
        return LocationResponse.from(locations.reportSighting(publicId, body));
    }
}
