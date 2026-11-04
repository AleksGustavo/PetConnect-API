package com.petconnect.api.location.application;

import com.petconnect.api.location.domain.Location;
import com.petconnect.api.location.domain.LocationSource;
import com.petconnect.api.location.domain.PositionSource;
import com.petconnect.api.location.infrastructure.LocationRepository;
import com.petconnect.api.location.web.LocationRequest;
import com.petconnect.api.location.web.SightingRequest;
import com.petconnect.api.pet.application.PetService;
import com.petconnect.api.shared.error.ApiException;
import com.petconnect.api.shared.web.RateLimiter;
import com.petconnect.api.sharing.application.PublicSharingService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Casos de uso de localização: a lista autenticada do tutor (RF32) e os dois
 * endpoints públicos sem login que a página do QR usa (RF17–19, RF31).
 */
@Service
public class LocationService {

    /** Além do limite por IP do filtro público: um pet não vira alvo de spam por vários IPs. */
    static final int SIGHTINGS_PER_PET_PER_HOUR = 20;

    private final LocationRepository locations;
    private final PetService pets;
    private final RateLimiter limiter;

    public LocationService(LocationRepository locations, PetService pets, RateLimiter limiter) {
        this.locations = locations;
        this.pets = pets;
        this.limiter = limiter;
    }

    // ---------------------------------------------------------- autenticado (tutor)

    public List<Location> list(String tutorId, String petId) {
        pets.get(tutorId, petId);
        return locations.findByPetIdOrderByReportedAtDesc(petId);
    }

    public Location createForTutor(String tutorId, String petId, LocationRequest req) {
        pets.get(tutorId, petId);
        Location l = new Location();
        l.setPetId(petId);
        l.setSource(LocationSource.TUTOR);
        l.setReportedAt(req.reportedAt() == null
                ? Instant.now()
                : req.reportedAt().atStartOfDay(ZoneOffset.UTC).toInstant());
        l.setLatitude(req.latitude());
        l.setLongitude(req.longitude());
        l.setDescription(req.description());
        l.setReporterContact(req.reporterContact());
        return locations.save(l);
    }

    // ---------------------------------------------------------- público (sem auth)

    /**
     * Relato anônimo da página do QR. O pet já vem resolvido e validado por
     * {@code PublicSharingService.resolveForSighting}. {@code legacyPetId}
     * guarda o id do Firestore: o pet pode ainda não estar em {@code pets}
     * ({@code petId} nulo) ou ganhar outro {@code _id} na próxima migração.
     */
    public Optional<Location> reportSighting(PublicSharingService.ResolvedPet pet, SightingRequest req) {
        if ((req.latitude() == null) != (req.longitude() == null)) {
            throw ApiException.badRequest("Envie latitude e longitude juntas.");
        }
        String description = blankToNull(req.description());
        if (!req.hasCoordinates() && description == null) {
            throw ApiException.badRequest("Conte onde viu o pet ou compartilhe a localização.");
        }
        if (req.clientRequestId() != null && locations.existsByClientRequestId(req.clientRequestId())) {
            return Optional.empty();
        }
        if (!limiter.tryConsume("sighting:" + pet.petKey(), SIGHTINGS_PER_PET_PER_HOUR, Duration.ofHours(1))) {
            throw ApiException.tooManyRequests("Este pet recebeu muitos relatos agora. Tente novamente mais tarde.");
        }

        Location l = new Location();
        l.setPetId(pet.apiPetId());
        l.setLegacyPetId(pet.firestoreId());
        l.setSource(LocationSource.PUBLIC_QR);
        l.setReportedAt(Instant.now());
        l.setLatitude(req.latitude());
        l.setLongitude(req.longitude());
        l.setAccuracyMeters(req.hasCoordinates() ? req.accuracyMeters() : null);
        l.setPositionSource(req.hasCoordinates() ? PositionSource.BROWSER_GEOLOCATION : PositionSource.NONE);
        l.setDescription(description);
        l.setReporterContact(blankToNull(req.reporterContact()));
        l.setClientRequestId(req.clientRequestId());
        try {
            return Optional.of(locations.save(l));
        } catch (DuplicateKeyException sameRequestAtTheSameTime) {
            return Optional.empty();
        }
    }

    /**
     * Relatos que a página do QR recebeu para este pet, mais recentes primeiro.
     * Inclui os que ficaram só com o id do Firestore ({@code petId} nulo ou de
     * uma migração anterior). Os registros do próprio tutor ficam de fora: no
     * app em modo Firestore eles já vêm de {@code Pets/{id}/localizacoes}.
     */
    public List<Location> sightingsFor(PublicSharingService.ResolvedPet pet, int limit) {
        Map<String, Location> byId = new LinkedHashMap<>();
        PageRequest page = PageRequest.of(0, limit);
        if (pet.firestoreId() != null) {
            locations.findByLegacyPetIdOrderByReportedAtDesc(pet.firestoreId(), page).forEach(l -> byId.put(l.getId(), l));
        }
        if (pet.apiPetId() != null) {
            locations.findByPetIdOrderByReportedAtDesc(pet.apiPetId(), page).forEach(l -> byId.put(l.getId(), l));
        }
        return byId.values().stream()
                .filter(l -> l.getSource() == LocationSource.PUBLIC_QR)
                .sorted(Comparator.comparing(Location::getReportedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .toList();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}
