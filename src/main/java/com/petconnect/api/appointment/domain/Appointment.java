package com.petconnect.api.appointment.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Consulta veterinária agendada para um pet. Coleção {@code appointments}. */
@Document(collection = "appointments")
public class Appointment {

    @Id
    private String id;

    @Indexed
    private String petId;

    private LocalDate scheduledDate;

    /** Horário opcional — o app permite agendar sem hora definida. */
    private LocalTime scheduledTime;

    private String veterinarian;
    private String reason;
    private AppointmentStatus status = AppointmentStatus.CONFIRMED;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public String getPetId() {
        return petId;
    }

    public void setPetId(String petId) {
        this.petId = petId;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public void setScheduledDate(LocalDate scheduledDate) {
        this.scheduledDate = scheduledDate;
    }

    public LocalTime getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(LocalTime scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public String getVeterinarian() {
        return veterinarian;
    }

    public void setVeterinarian(String veterinarian) {
        this.veterinarian = veterinarian;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
