package com.petconnect.api.location.application;

import com.petconnect.api.location.domain.Location;
import com.petconnect.api.location.domain.LocationSource;
import com.petconnect.api.location.infrastructure.LocationRepository;
import com.petconnect.api.location.web.LocationRequest;
import com.petconnect.api.location.web.SightingRequest;
import com.petconnect.api.pet.application.PetService;
import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.pet.domain.PetStatus;
import com.petconnect.api.pet.infrastructure.PetRepository;
import com.petconnect.api.shared.error.ApiException;
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
    private final PetRepository petRepository;

    public LocationService(LocationRepository locations, PetService pets, PetRepository petRepository) {
        this.locations = locations;
        this.pets = pets;
        this.petRepository = petRepository;
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

    /** Pet arquivado não aparece na página pública — evita reviver um caso encerrado. */
    public Pet publicSummary(String publicId) {
        Pet p = petRepository.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.notFound("Pet não encontrado."));
        if (p.getStatus() == PetStatus.ARCHIVED) {
            throw ApiException.notFound("Pet não encontrado.");
        }
        return p;
    }

    public Location reportSighting(String publicId, SightingRequest req) {
        Pet p = publicSummary(publicId);
        Location l = new Location();
        l.setPetId(p.getId());
        l.setSource(LocationSource.PUBLIC_QR);
        l.setReportedAt(Instant.now());
        l.setLatitude(req.latitude());
        l.setLongitude(req.longitude());
        l.setDescription(req.description());
        l.setReporterContact(req.reporterContact());
        return locations.save(l);
    }
}
