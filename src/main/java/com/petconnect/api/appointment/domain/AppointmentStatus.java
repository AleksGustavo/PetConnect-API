package com.petconnect.api.appointment.domain;

/**
 * Status de uma consulta veterinária. O app hoje só usa
 * {@code CONFIRMED}/{@code COMPLETED}/{@code CANCELLED} (equivalentes a
 * agendada/realizada/cancelada); os demais preparam o fluxo
 * clínica↔tutor futuro (ver docs/database/mongodb-target-schema.md).
 */
public enum AppointmentStatus {
    REQUESTED,
    PENDING,
    CONFIRMED,
    COMPLETED,
    CANCELLATION_REQUESTED,
    CANCELLED,
    REJECTED
}
