package com.petconnect.api.location.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Registro de localização de um pet (scan de QR ou registro do tutor).
 * Coleção {@code locations}. Origem histórica: coleção raiz {@code Localizacoes}
 * do Firestore. Ver {@code docs/database/mongodb-target-schema.md}.
 */
@Document(collection = "locations")
public class Location {

    @Id
    private String id;

    /** {@code null} quando o pet do registro legado já não existe. */
    @Indexed
    private String petId;

    @Indexed
    private String legacyPetId;
    private String legacyPetName;
    private String legacyTutorName;

    private Instant reportedAt;
    private Double latitude;
    private Double longitude;
    /** Raio de incerteza informado pelo navegador de quem relatou, em metros. */
    private Double accuracyMeters;
    /** De onde veio a coordenada; {@code null} nos registros anteriores à fase 3 (#37). */
    private PositionSource positionSource;
    private String description;
    private String reporterContact;
    private LocationSource source = LocationSource.PUBLIC_QR;

    /** Id gerado pela página para cada envio: repetir o envio não duplica o relato. */
    @Indexed(unique = true, sparse = true)
    private String clientRequestId;

    private boolean legacyImport;
    private List<String> migrationWarnings = new ArrayList<>();

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

    public String getLegacyPetId() {
        return legacyPetId;
    }

    public void setLegacyPetId(String legacyPetId) {
        this.legacyPetId = legacyPetId;
    }

    public String getLegacyPetName() {
        return legacyPetName;
    }

    public void setLegacyPetName(String legacyPetName) {
        this.legacyPetName = legacyPetName;
    }

    public String getLegacyTutorName() {
        return legacyTutorName;
    }

    public void setLegacyTutorName(String legacyTutorName) {
        this.legacyTutorName = legacyTutorName;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public void setReportedAt(Instant reportedAt) {
        this.reportedAt = reportedAt;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getAccuracyMeters() {
        return accuracyMeters;
    }

    public void setAccuracyMeters(Double accuracyMeters) {
        this.accuracyMeters = accuracyMeters;
    }

    public PositionSource getPositionSource() {
        return positionSource;
    }

    public void setPositionSource(PositionSource positionSource) {
        this.positionSource = positionSource;
    }

    public String getClientRequestId() {
        return clientRequestId;
    }

    public void setClientRequestId(String clientRequestId) {
        this.clientRequestId = clientRequestId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReporterContact() {
        return reporterContact;
    }

    public void setReporterContact(String reporterContact) {
        this.reporterContact = reporterContact;
    }

    public LocationSource getSource() {
        return source;
    }

    public void setSource(LocationSource source) {
        this.source = source;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
