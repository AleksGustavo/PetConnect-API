package com.petconnect.api.appointment.web;

import com.petconnect.api.appointment.application.AppointmentService;
import com.petconnect.api.shared.security.AuthenticatedUser;
import com.petconnect.api.shared.web.ApiV1;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consultas veterinárias de um pet do tutor autenticado. Sem
 * {@code DELETE} — cancelar é uma mudança de {@code status} (RF29).
 */
@RestController
@RequestMapping(ApiV1.BASE + "/pets/{petId}/appointments")
@Tag(name = "appointments")
public class AppointmentController {

    private final AppointmentService appointments;

    public AppointmentController(AppointmentService appointments) {
        this.appointments = appointments;
    }

    @GetMapping
    @Operation(summary = "Lista as consultas do pet (ordem cronológica)")
    public List<AppointmentResponse> list(@AuthenticationPrincipal AuthenticatedUser me,
                                          @PathVariable String petId) {
        return appointments.list(me.userId(), petId).stream().map(AppointmentResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Agenda uma consulta")
    public AppointmentResponse create(@AuthenticationPrincipal AuthenticatedUser me,
                                      @PathVariable String petId,
                                      @Valid @RequestBody AppointmentRequest body) {
        return AppointmentResponse.from(appointments.create(me.userId(), petId, body));
    }

    @PatchMapping("/{appointmentId}")
    @Operation(summary = "Edita uma consulta (dados, ou muda o status para cancelar/marcar como realizada)")
    public AppointmentResponse update(@AuthenticationPrincipal AuthenticatedUser me,
                                      @PathVariable String petId,
                                      @PathVariable String appointmentId,
                                      @Valid @RequestBody AppointmentRequest body) {
        return AppointmentResponse.from(appointments.update(me.userId(), petId, appointmentId, body));
    }
}
