package com.petconnect.api.medicalrecord.web;

import com.petconnect.api.medicalrecord.application.MedicalRecordService;
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

/** Histórico médico de um pet do tutor autenticado. */
@RestController
@RequestMapping(ApiV1.BASE + "/pets/{petId}/medical-records")
@Tag(name = "medical-records")
public class MedicalRecordController {

    private final MedicalRecordService records;

    public MedicalRecordController(MedicalRecordService records) {
        this.records = records;
    }

    @GetMapping
    @Operation(summary = "Lista o histórico médico do pet (ordem cronológica)")
    public List<MedicalRecordResponse> list(@AuthenticationPrincipal AuthenticatedUser me,
                                            @PathVariable String petId) {
        return records.list(me.userId(), petId).stream().map(MedicalRecordResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registra uma entrada de histórico médico (aceita id pré-gerado pelo app)")
    public MedicalRecordResponse create(@AuthenticationPrincipal AuthenticatedUser me,
                                        @PathVariable String petId,
                                        @Valid @RequestBody MedicalRecordRequest body) {
        return MedicalRecordResponse.from(records.create(me.userId(), petId, body));
    }

    @PatchMapping("/{recordId}")
    @Operation(summary = "Edita um registro de histórico médico")
    public MedicalRecordResponse update(@AuthenticationPrincipal AuthenticatedUser me,
                                        @PathVariable String petId,
                                        @PathVariable String recordId,
                                        @Valid @RequestBody MedicalRecordRequest body) {
        return MedicalRecordResponse.from(records.update(me.userId(), petId, recordId, body));
    }

    @DeleteMapping("/{recordId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui um registro de histórico médico")
    public void delete(@AuthenticationPrincipal AuthenticatedUser me,
                       @PathVariable String petId,
                       @PathVariable String recordId) {
        records.delete(me.userId(), petId, recordId);
    }
}
