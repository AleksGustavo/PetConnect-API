package com.petconnect.api.vaccine.web;

import com.petconnect.api.shared.security.AuthenticatedUser;
import com.petconnect.api.shared.web.ApiV1;
import com.petconnect.api.vaccine.application.VaccineService;
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

/** Carteira de vacina de um pet do tutor autenticado. */
@RestController
@RequestMapping(ApiV1.BASE + "/pets/{petId}/vaccines")
@Tag(name = "vaccines")
public class VaccineController {

    private final VaccineService vaccines;

    public VaccineController(VaccineService vaccines) {
        this.vaccines = vaccines;
    }

    @GetMapping
    @Operation(summary = "Lista as vacinas do pet (ordem cronológica)")
    public List<VaccineResponse> list(@AuthenticationPrincipal AuthenticatedUser me,
                                      @PathVariable String petId) {
        return vaccines.list(me.userId(), petId).stream().map(VaccineResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra uma vacina aplicada")
    public VaccineResponse create(@AuthenticationPrincipal AuthenticatedUser me,
                                  @PathVariable String petId,
                                  @Valid @RequestBody VaccineRequest body) {
        return VaccineResponse.from(vaccines.create(me.userId(), petId, body));
    }

    @PatchMapping("/{vaccineId}")
    @Operation(summary = "Edita um registro de vacina")
    public VaccineResponse update(@AuthenticationPrincipal AuthenticatedUser me,
                                  @PathVariable String petId,
                                  @PathVariable String vaccineId,
                                  @Valid @RequestBody VaccineRequest body) {
        return VaccineResponse.from(vaccines.update(me.userId(), petId, vaccineId, body));
    }

    @DeleteMapping("/{vaccineId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui um registro de vacina")
    public void delete(@AuthenticationPrincipal AuthenticatedUser me,
                       @PathVariable String petId,
                       @PathVariable String vaccineId) {
        vaccines.delete(me.userId(), petId, vaccineId);
    }
}
