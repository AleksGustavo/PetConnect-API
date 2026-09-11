package com.petconnect.api.location.web;

import com.petconnect.api.location.domain.Location;

import java.time.Instant;

/** Representação de um registro de localização para o tutor autenticado. */
public record LocationResponse(
        String id,
        String petId,
        Instant reportedAt,
        Double latitude,
        Double longitude,
        String description,
        String reporterContact,
        String source,
        boolean legacyImport
) {

    public static LocationResponse from(Location l) {
        return new LocationResponse(
                l.getId(),
                l.getPetId(),
                l.getReportedAt(),
                l.getLatitude(),
                l.getLongitude(),
                l.getDescription(),
                l.getReporterContact(),
                l.getSource() == null ? null : l.getSource().name(),
                l.isLegacyImport());
    }
}
