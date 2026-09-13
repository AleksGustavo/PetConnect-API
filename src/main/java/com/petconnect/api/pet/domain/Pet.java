package com.petconnect.api.pet.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pet. Coleção {@code pets}. Ver {@code docs/database/mongodb-target-schema.md}.
 * A FASE 3 só popula o suficiente para a migração; as FASES 5+ acrescentam a API.
 */
@Document(collection = "pets")
public class Pet {

    @Id
    private String id;

    /** UUID aleatório usado na URL pública do QR ({@code /p/{publicId}}). */
    @Indexed(unique = true)
    private String publicId = UUID.randomUUID().toString();

    @Indexed
    private String tutorId;

    private String name;
    private Species species = Species.OTHER;
    private String breed;
    private String color;
    private PetGender gender = PetGender.UNKNOWN;
    private PetSize size;
    private Double weightKg;
    private LocalDate birthDate;
    private PetStatus status = PetStatus.ACTIVE;
    private Boolean vaccinatedFlag;
    private String publicContactPhone;
    private String photoUrl;
    private String coverPhotoUrl;

    /**
     * Alinhamento vertical da capa dentro do cabeçalho, de -1.0 (topo) a 1.0
     * (base); 0.0 (ou {@code null}) é o centro. Mesma escala do eixo Y de
     * {@code Alignment} no Flutter — deixa o tutor escolher qual parte da
     * foto aparece, em vez do recorte automático.
     */
    private Double coverPhotoAlignY;

    private String legacyQrCodeId;
    private String legacyFirestoreId;
    private boolean legacyImport;
    private List<String> migrationWarnings = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;
    private Instant deletedAt;

    public String getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public String getTutorId() {
        return tutorId;
    }

    public void setTutorId(String tutorId) {
        this.tutorId = tutorId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Species getSpecies() {
        return species;
    }

    public void setSpecies(Species species) {
        this.species = species;
    }

    public String getBreed() {
        return breed;
    }

    public void setBreed(String breed) {
        this.breed = breed;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public PetGender getGender() {
        return gender;
    }

    public void setGender(PetGender gender) {
        this.gender = gender;
    }

    public PetSize getSize() {
        return size;
    }

    public void setSize(PetSize size) {
        this.size = size;
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public PetStatus getStatus() {
        return status;
    }

    public void setStatus(PetStatus status) {
        this.status = status;
    }

    public Boolean getVaccinatedFlag() {
        return vaccinatedFlag;
    }

    public void setVaccinatedFlag(Boolean vaccinatedFlag) {
        this.vaccinatedFlag = vaccinatedFlag;
    }

    public String getPublicContactPhone() {
        return publicContactPhone;
    }

    public void setPublicContactPhone(String publicContactPhone) {
        this.publicContactPhone = publicContactPhone;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getCoverPhotoUrl() {
        return coverPhotoUrl;
    }

    public void setCoverPhotoUrl(String coverPhotoUrl) {
        this.coverPhotoUrl = coverPhotoUrl;
    }

    public Double getCoverPhotoAlignY() {
        return coverPhotoAlignY;
    }

    public void setCoverPhotoAlignY(Double coverPhotoAlignY) {
        this.coverPhotoAlignY = coverPhotoAlignY;
    }

    public String getLegacyQrCodeId() {
        return legacyQrCodeId;
    }

    public void setLegacyQrCodeId(String legacyQrCodeId) {
        this.legacyQrCodeId = legacyQrCodeId;
    }

    public String getLegacyFirestoreId() {
        return legacyFirestoreId;
    }

    public void setLegacyFirestoreId(String legacyFirestoreId) {
        this.legacyFirestoreId = legacyFirestoreId;
    }

    public boolean isLegacyImport() {
        return legacyImport;
    }

    public void setLegacyImport(boolean legacyImport) {
        this.legacyImport = legacyImport;
    }

    public List<String> getMigrationWarnings() {
        return migrationWarnings;
    }

    public void setMigrationWarnings(List<String> migrationWarnings) {
        this.migrationWarnings = migrationWarnings;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
