package com.petconnect.api.vaccine.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;

/** Registro da carteira de vacina de um pet. Coleção {@code vaccines}. */
@Document(collection = "vaccines")
public class Vaccine {

    @Id
    private String id;

    @Indexed
    private String petId;

    private String name;
    private LocalDate appliedAt;
    private LocalDate nextDoseAt;
    private String veterinarian;
    private String notes;

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDate appliedAt) {
        this.appliedAt = appliedAt;
    }

    public LocalDate getNextDoseAt() {
        return nextDoseAt;
    }

    public void setNextDoseAt(LocalDate nextDoseAt) {
        this.nextDoseAt = nextDoseAt;
    }

    public String getVeterinarian() {
        return veterinarian;
    }

    public void setVeterinarian(String veterinarian) {
        this.veterinarian = veterinarian;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
