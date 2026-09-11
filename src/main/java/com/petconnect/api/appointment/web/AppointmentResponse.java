package com.petconnect.api.appointment.web;

import com.petconnect.api.appointment.domain.Appointment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Representação pública de uma consulta. */
public record AppointmentResponse(
        String id,
        String petId,
        LocalDate scheduledDate,
        LocalTime scheduledTime,
        String veterinarian,
        String reason,
        String status,
        Instant createdAt,
        Instant updatedAt
) {

    public static AppointmentResponse from(Appointment a) {
        return new AppointmentResponse(
                a.getId(),
                a.getPetId(),
                a.getScheduledDate(),
                a.getScheduledTime(),
                a.getVeterinarian(),
                a.getReason(),
                a.getStatus() == null ? null : a.getStatus().name(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }
}
