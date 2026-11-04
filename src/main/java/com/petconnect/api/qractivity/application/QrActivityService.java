package com.petconnect.api.qractivity.application;

import com.petconnect.api.location.application.LocationService;
import com.petconnect.api.location.domain.Location;
import com.petconnect.api.location.web.SightingRequest;
import com.petconnect.api.notification.application.TutorNotifier;
import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.pet.infrastructure.PetRepository;
import com.petconnect.api.qractivity.domain.QrAccessEvent;
import com.petconnect.api.qractivity.infrastructure.QrAccessEventRepository;
import com.petconnect.api.shared.security.AuthenticatedUser;
import com.petconnect.api.shared.web.RateLimiter;
import com.petconnect.api.sharing.application.PublicSharingService;
import com.petconnect.api.sharing.application.PublicSharingService.ResolvedPet;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * O que acontece com o QR de um pet (issue #37 do app): acessos à página
 * ({@code qr_page_opened}) e relatos com ou sem localização
 * ({@code sighting_location_shared}). Só o tutor do pet vê.
 */
@Service
public class QrActivityService {

    /** Acessos gravados por pet por hora; acima disso a página segue normal, sem gravar. */
    static final int ACCESSES_PER_PET_PER_HOUR = 30;
    static final int SIGHTINGS_SHOWN = 50;
    static final int ACCESSES_SHOWN = 20;
    static final Duration ACCESS_COUNT_WINDOW = Duration.ofDays(30);

    private final PublicSharingService sharing;
    private final LocationService locations;
    private final QrAccessEventRepository accesses;
    private final PetRepository pets;
    private final RateLimiter limiter;
    private final TutorNotifier notifier;

    public QrActivityService(PublicSharingService sharing, LocationService locations,
                             QrAccessEventRepository accesses, PetRepository pets, RateLimiter limiter,
                             TutorNotifier notifier) {
        this.sharing = sharing;
        this.locations = locations;
        this.accesses = accesses;
        this.pets = pets;
        this.limiter = limiter;
        this.notifier = notifier;
    }

    /**
     * A página carregou um pet válido. Pet excluído, no Memorial ou com QR
     * revogado responde 404, como a própria página.
     *
     * @return vazio quando o pet já passou do limite de acessos gravados na hora
     */
    public Optional<QrAccessEvent> recordPageOpened(String publicId) {
        ResolvedPet pet = sharing.resolveForSighting(publicId);
        if (!limiter.tryConsume("qr-open:" + pet.petKey(), ACCESSES_PER_PET_PER_HOUR, Duration.ofHours(1))) {
            return Optional.empty();
        }
        QrAccessEvent saved = accesses.save(
                QrAccessEvent.pageOpened(pet.petKey(), pet.apiPetId(), pet.firestoreId(), Instant.now()));
        notifier.qrPageOpened(pet);
        return Optional.of(saved);
    }

    /**
     * Relato da página do QR, com ou sem localização. Avisa o tutor só quando
     * o relato é novo: o mesmo envio repetido não gera outro aviso.
     */
    public Optional<Location> reportSighting(String publicId, SightingRequest body) {
        ResolvedPet pet = sharing.resolveForSighting(publicId);
        Optional<Location> saved = locations.reportSighting(pet, body);
        saved.ifPresent(l -> notifier.sightingReported(pet, l.getLatitude() != null));
        return saved;
    }

    public record Activity(List<Location> sightings, List<QrAccessEvent> recentAccesses, long accessesLast30Days) {
    }

    public Activity forFirestorePet(AuthenticatedUser me, String firestoreId) {
        sharing.ownedFirestorePet(me, firestoreId);
        String apiPetId = pets.findByLegacyFirestoreId(firestoreId)
                .filter(p -> p.getDeletedAt() == null)
                .map(Pet::getId)
                .orElse(null);
        return activity(new ResolvedPet(apiPetId, firestoreId));
    }

    public Activity forApiPet(AuthenticatedUser me, String petId) {
        Pet pet = sharing.ownedApiPet(me, petId);
        return activity(new ResolvedPet(pet.getId(), pet.getLegacyFirestoreId()));
    }

    private Activity activity(ResolvedPet pet) {
        return new Activity(
                locations.sightingsFor(pet, SIGHTINGS_SHOWN),
                accesses.findByPetKeyOrderByOccurredAtDesc(pet.petKey(), PageRequest.of(0, ACCESSES_SHOWN)),
                accesses.countByPetKeyAndOccurredAtAfter(pet.petKey(), Instant.now().minus(ACCESS_COUNT_WINDOW)));
    }
}
