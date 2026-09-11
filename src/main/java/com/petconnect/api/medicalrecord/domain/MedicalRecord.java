package com.petconnect.api.medicalrecord.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Entrada de histórico médico de um pet (exames, laudos, anotações), com
 * anexos hospedados no Cloudinary. Coleção {@code medical_records}.
 */
@Document(collection = "medical_records")
public class MedicalRecord {

    /**
     * Pode vir pré-gerado pelo app (ver {@code ApiHistoricoMedicoRepository}):
     * os anexos sobem para o Cloudinary usando esse id antes de o registro
     * existir, então o cliente escolhe o id na criação em vez do Mongo gerar
     * um {@code ObjectId}.
     */
    @Id
    private String id;

    @Indexed
    private String petId;

    private LocalDate recordedAt;
    private String description;
    private String veterinarian;
    private List<String> attachments = new ArrayList<>();
    private MedicalOrigin origin = MedicalOrigin.TUTOR;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPetId() {
        return petId;
    }

    public void setPetId(String petId) {
        this.petId = petId;
    }

    public LocalDate getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDate recordedAt) {
        this.recordedAt = recordedAt;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVeterinarian() {
        return veterinarian;
    }

    public void setVeterinarian(String veterinarian) {
        this.veterinarian = veterinarian;
    }

    public List<String> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<String> attachments) {
        this.attachments = attachments;
    }

    public MedicalOrigin getOrigin() {
        return origin;
    }

    public void setOrigin(MedicalOrigin origin) {
        this.origin = origin;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
