package com.petconnect.api.qractivity.web;

import com.petconnect.api.location.domain.Location;
import com.petconnect.api.qractivity.application.QrActivityService.Activity;

import java.time.Instant;
import java.util.List;

/**
 * Atividade do QR de um pet para o tutor (issue #37 do app). Avistamentos e
 * acessos vêm separados: um acesso nunca é apresentado como avistamento.
 */
public record QrActivityResponse(List<Sighting> sightings, List<Access> recentAccesses, long accessesLast30Days) {

    /**
     * Relato da página do QR. A coordenada é a que o navegador de quem relatou
     * informou, com a precisão que ele deu; nada disso é verificado.
     */
    public record Sighting(String id, Instant reportedAt, Double latitude, Double longitude, Double accuracyMeters,
                           String positionSource, String description, String reporterContact) {

        static Sighting from(Location l) {
            return new Sighting(l.getId(), l.getReportedAt(), l.getLatitude(), l.getLongitude(), l.getAccuracyMeters(),
                    l.getPositionSource() == null ? null : l.getPositionSource().name(),
                    l.getDescription(), l.getReporterContact());
        }
    }

    public record Access(Instant occurredAt) {
    }

    public static QrActivityResponse from(Activity a) {
        return new QrActivityResponse(
                a.sightings().stream().map(Sighting::from).toList(),
                a.recentAccesses().stream().map(e -> new Access(e.getOccurredAt())).toList(),
                a.accessesLast30Days());
    }
}
