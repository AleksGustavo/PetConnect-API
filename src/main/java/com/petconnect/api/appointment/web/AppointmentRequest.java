package com.petconnect.api.appointment.web;

import com.petconnect.api.appointment.domain.AppointmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Corpo do {@code POST} e do {@code PATCH} de consulta. O app sempre envia o
 * registro completo; {@code status} nulo vira {@code CONFIRMED} na criação e
 * mantém o valor atual na edição.
 */
public record AppointmentRequest(
        @NotNull LocalDate scheduledDate,
        LocalTime scheduledTime,
        @NotBlank @Size(max = 120) String veterinarian,
        @NotBlank @Size(max = 500) String reason,
        AppointmentStatus status
) {
}
