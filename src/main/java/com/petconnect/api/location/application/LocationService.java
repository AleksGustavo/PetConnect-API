package com.petconnect.api.location.application;

import com.petconnect.api.location.domain.Location;
import com.petconnect.api.location.domain.LocationSource;
import com.petconnect.api.location.infrastructure.LocationRepository;
import com.petconnect.api.location.web.LocationRequest;
import com.petconnect.api.location.web.SightingRequest;
import com.petconnect.api.pet.application.PetService;
import com.petconnect.api.sharing.application.PublicSharingService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Casos de uso de localização: a lista autenticada do tutor (RF32) e os dois
 * endpoints públicos sem login que a página do QR usa (RF17–19, RF31).
 */
@Service
public class LocationService {

    private final LocationRepository locations;
    private final PetService pets;

    public LocationService(LocationRepository locations, PetService pets) {
        this.locations = locations;
        this.pets = pets;
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
    public Location reportSighting(PublicSharingService.ResolvedPet pet, SightingRequest req) {
        Location l = new Location();
        l.setPetId(pet.apiPetId());
        l.setLegacyPetId(pet.firestoreId());
        l.setSource(LocationSource.PUBLIC_QR);
        l.setReportedAt(Instant.now());
        l.setLatitude(req.latitude());
        l.setLongitude(req.longitude());
        l.setDescription(req.description());
        l.setReporterContact(req.reporterContact());
        return locations.save(l);
    }
}
