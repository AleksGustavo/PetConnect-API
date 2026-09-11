package com.petconnect.api.location.web;

import com.petconnect.api.location.application.LocationService;
import com.petconnect.api.shared.security.AuthenticatedUser;
import com.petconnect.api.shared.web.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Avistamentos de um pet do tutor autenticado (RF32) — inclui o histórico
 * migrado do Firestore e os relatos anônimos recebidos pelo QR
 * ({@link PublicPetController}).
 */
@RestController
@RequestMapping(ApiV1.BASE + "/pets/{petId}/locations")
@Tag(name = "locations")
public class LocationController {

    private final LocationService locations;

    public LocationController(LocationService locations) {
        this.locations = locations;
    }

    @GetMapping
    @Operation(summary = "Lista os avistamentos do pet (mais recentes primeiro)")
    public List<LocationResponse> list(@AuthenticationPrincipal AuthenticatedUser me,
                                       @PathVariable String petId) {
        return locations.list(me.userId(), petId).stream().map(LocationResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra um avistamento manualmente (RF31/32)")
    public LocationResponse create(@AuthenticationPrincipal AuthenticatedUser me,
                                   @PathVariable String petId,
                                   @Valid @RequestBody LocationRequest body) {
        return LocationResponse.from(locations.createForTutor(me.userId(), petId, body));
    }
}
