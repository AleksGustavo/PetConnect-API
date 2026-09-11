package com.petconnect.api.pet.web;

import com.petconnect.api.pet.application.PetService;
import com.petconnect.api.shared.security.AuthenticatedUser;
import com.petconnect.api.shared.web.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Pets do tutor autenticado. A posse é sempre validada no servidor. */
@RestController
@RequestMapping(ApiV1.BASE + "/pets")
@Tag(name = "pets")
public class PetController {

    private final PetService pets;

    public PetController(PetService pets) {
        this.pets = pets;
    }

    @GetMapping
    @Operation(summary = "Lista os pets do tutor autenticado")
    public List<PetResponse> list(@AuthenticationPrincipal AuthenticatedUser me) {
        return pets.list(me.userId()).stream().map(PetResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um pet do tutor autenticado")
    public PetResponse get(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable String id) {
        return PetResponse.from(pets.get(me.userId(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um pet")
    public PetResponse create(@AuthenticationPrincipal AuthenticatedUser me,
                              @Valid @RequestBody CreatePetRequest body) {
        return PetResponse.from(pets.create(me.userId(), body));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Atualiza campos de um pet")
    public PetResponse update(@AuthenticationPrincipal AuthenticatedUser me,
                              @PathVariable String id,
                              @Valid @RequestBody UpdatePetRequest body) {
        return PetResponse.from(pets.update(me.userId(), id, body));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui um pet (cascata de localizações)")
    public void delete(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable String id) {
        pets.delete(me.userId(), id);
    }
}
