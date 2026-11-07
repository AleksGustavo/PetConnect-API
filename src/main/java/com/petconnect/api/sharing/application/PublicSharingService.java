package com.petconnect.api.sharing.application;

import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.pet.domain.PetStatus;
import com.petconnect.api.pet.infrastructure.PetRepository;
import com.petconnect.api.shared.error.ApiException;
import com.petconnect.api.shared.security.AuthenticatedUser;
import com.petconnect.api.sharing.application.FirestorePets.FirestorePet;
import com.petconnect.api.sharing.application.FirestorePets.GalleryPhoto;
import com.petconnect.api.sharing.domain.ConsentEvent;
import com.petconnect.api.sharing.domain.PublicField;
import com.petconnect.api.sharing.domain.PublicSharing;
import com.petconnect.api.sharing.infrastructure.ConsentEventRepository;
import com.petconnect.api.sharing.infrastructure.PublicSharingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Página pública do QR com consentimento (issue #35 do app, fase 1 do épico
 * #102). O tutor escolhe campo a campo o que aparece e aceita um texto de
 * consentimento versionado; a resposta pública devolve só esses campos.
 *
 * <p>Sem consentimento ativo, a página recebe o mínimo neutro
 * ({@code shared: false}): o QR continua abrindo e quem achou o pet ainda
 * consegue avisar o tutor, sem ver nome, foto ou contato.
 *
 * <p>Pets do app em modo Firestore são lidos no Firestore a cada acesso
 * ({@link FirestorePets}): a posse vem de lá, pets ainda não migrados para o
 * MongoDB funcionam, e um pet excluído ou no Memorial some da página na hora.
 */
@Service
public class PublicSharingService {

    private static final Logger log = LoggerFactory.getLogger(PublicSharingService.class);

    /**
     * Versão do texto de consentimento que o app mostra. Mudar o texto exige
     * mudar este valor e o do app; publicações em outra versão deixam de
     * valer até o tutor aceitar de novo.
     */
    public static final String CONSENT_VERSION = "2026-11-02";
    static final int MAX_TUTOR_NAME = 60;
    /** Fotos da galeria além da de perfil (issue #103 do app): exatamente duas. */
    static final int GALLERY_PHOTO_COUNT = 2;
    /** Id de documento do Firestore como o app gera; nada de barra ou caminho. */
    private static final Pattern GALLERY_PHOTO_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private final PublicSharingRepository sharings;
    private final ConsentEventRepository events;
    private final PetRepository pets;
    private final FirestorePets firestore;

    public PublicSharingService(PublicSharingRepository sharings, ConsentEventRepository events,
                                PetRepository pets, FirestorePets firestore) {
        this.sharings = sharings;
        this.events = events;
        this.pets = pets;
        this.firestore = firestore;
    }

    // ---------------------------------------------------------- tutor

    public PublicSharing currentForFirestorePet(AuthenticatedUser me, String firestoreId) {
        ownedFirestorePet(me, firestoreId);
        return sharings.findByPetKey(PublicSharing.firestoreKey(firestoreId))
                .orElseGet(() -> newForFirestorePet(me, firestoreId));
    }

    public PublicSharing updateForFirestorePet(AuthenticatedUser me, String firestoreId, SharingChange change) {
        FirestorePet pet = ownedFirestorePet(me, firestoreId);
        if (change.enabled() && pet.deceased()) {
            throw memorialConflict();
        }
        List<String> gallery = galleryPhotosFor(change, firestoreId, pet);
        PublicSharing s = sharings.findByPetKey(PublicSharing.firestoreKey(firestoreId))
                .orElseGet(() -> newForFirestorePet(me, firestoreId));
        return apply(s, change, gallery, me.firebaseUid());
    }

    public PublicSharing currentForApiPet(AuthenticatedUser me, String petId) {
        ownedApiPet(me, petId);
        return sharings.findByPetKey(PublicSharing.apiKey(petId))
                .orElseGet(() -> PublicSharing.forApiPet(petId, me.firebaseUid()));
    }

    public PublicSharing updateForApiPet(AuthenticatedUser me, String petId, SharingChange change) {
        Pet pet = ownedApiPet(me, petId);
        if (change.enabled() && pet.getStatus() == PetStatus.DECEASED) {
            throw memorialConflict();
        }
        if (change.enabled() && wantsGallery(change)) {
            // A galeria só existe no Firestore (Pets/{id}/fotos).
            throw ApiException.badRequest("As fotos da galeria só podem ser publicadas para pets do app.");
        }
        PublicSharing s = sharings.findByPetKey(PublicSharing.apiKey(petId))
                .orElseGet(() -> PublicSharing.forApiPet(petId, me.firebaseUid()));
        return apply(s, change, List.of(), me.firebaseUid());
    }

    /** O que o tutor pediu para a página; validado em {@link #apply}. */
    public record SharingChange(boolean enabled, boolean consentAccepted, String consentVersion,
                                Set<PublicField> fields, String tutorDisplayName, String contactPhone,
                                List<String> galleryPhotoIds) {
    }

    private static boolean wantsGallery(SharingChange change) {
        return change.fields() != null && change.fields().contains(PublicField.GALLERY_PHOTOS);
    }

    /**
     * As duas fotos da galeria pedidas, conferidas no Firestore: existem, são
     * deste pet e foram publicadas pelo dono. Vazia sem {@code GALLERY_PHOTOS}.
     * A foto de perfil vem primeiro no carrossel, por isso precisa estar
     * marcada e existir.
     */
    private List<String> galleryPhotosFor(SharingChange change, String firestoreId, FirestorePet pet) {
        if (!change.enabled() || !wantsGallery(change)) {
            return List.of();
        }
        if (!change.fields().contains(PublicField.PROFILE_PHOTO) || pet.photoUrl() == null) {
            throw ApiException.badRequest(
                    "As fotos da galeria aparecem depois da foto de perfil. Marque a foto de perfil do pet.");
        }
        List<String> ids = change.galleryPhotoIds() == null ? List.of() : change.galleryPhotoIds();
        if (ids.size() != GALLERY_PHOTO_COUNT
                || ids.stream().anyMatch(id -> id == null || !GALLERY_PHOTO_ID.matcher(id).matches())
                || Set.copyOf(ids).size() != GALLERY_PHOTO_COUNT) {
            throw ApiException.badRequest("Escolha duas fotos diferentes da galeria do pet.");
        }
        List<GalleryPhoto> found;
        try {
            found = firestore.galleryPhotos(firestoreId, ids);
        } catch (FirestorePets.Unavailable e) {
            log.warn("Firestore indisponível ao conferir as fotos da galeria: {}", e.getMessage());
            throw ApiException.unavailable("Não foi possível confirmar as fotos agora. Tente novamente em instantes.");
        }
        if (found.size() != GALLERY_PHOTO_COUNT
                || found.stream().anyMatch(p -> !pet.ownerUid().equals(p.ownerUid()))) {
            throw ApiException.badRequest("As fotos escolhidas não estão na galeria deste pet. Escolha de novo.");
        }
        return ids;
    }

    private PublicSharing apply(PublicSharing s, SharingChange change, List<String> galleryPhotoIds,
                                String actorUid) {
        Instant now = Instant.now();
        if (!change.enabled()) {
            boolean wasActive = s.isActive(CONSENT_VERSION);
            boolean hadConsent = s.getConsentAcceptedAt() != null && s.getConsentRevokedAt() == null;
            s.revoke(now);
            PublicSharing saved = sharings.save(s);
            if (wasActive || hadConsent) {
                events.save(ConsentEvent.of(s.getPetKey(), ConsentEvent.Type.REVOKED, Set.of(),
                        s.getConsentVersion(), actorUid, now));
                log.info("Página pública revogada ({}).", s.getPetKey());
            }
            return saved;
        }

        if (!change.consentAccepted()) {
            throw ApiException.badRequest("Marque a caixa de consentimento para publicar.");
        }
        if (!CONSENT_VERSION.equals(change.consentVersion())) {
            throw new ApiException(HttpStatus.CONFLICT, "CONSENT_VERSION_OUTDATED",
                    "O texto do consentimento mudou. Atualize o app e confirme de novo.");
        }
        Set<PublicField> fields = change.fields() == null || change.fields().isEmpty()
                ? EnumSet.noneOf(PublicField.class)
                : EnumSet.copyOf(change.fields());
        if (fields.isEmpty()) {
            throw ApiException.badRequest("Escolha ao menos um dado para mostrar na página.");
        }
        String tutorName = fields.contains(PublicField.TUTOR_NAME) ? tutorName(change.tutorDisplayName()) : null;
        String phone = fields.contains(PublicField.CONTACT_PHONE) ? ContactPhone.normalize(change.contactPhone()) : null;

        boolean wasActive = s.isActive(CONSENT_VERSION);
        s.publish(fields, tutorName, phone, galleryPhotoIds, CONSENT_VERSION, actorUid, now);
        PublicSharing saved = sharings.save(s);
        events.save(ConsentEvent.of(s.getPetKey(),
                wasActive ? ConsentEvent.Type.UPDATED : ConsentEvent.Type.ACCEPTED,
                fields, CONSENT_VERSION, actorUid, now));
        log.info("Página pública {} ({}, campos {}).", wasActive ? "atualizada" : "publicada", s.getPetKey(), fields);
        return saved;
    }

    private static String tutorName(String raw) {
        String name = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (name.isEmpty() || name.length() > MAX_TUTOR_NAME || name.codePoints().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("Informe o nome do tutor com até " + MAX_TUTOR_NAME + " caracteres.");
        }
        return name;
    }

    private PublicSharing newForFirestorePet(AuthenticatedUser me, String firestoreId) {
        return PublicSharing.forFirestorePet(firestoreId, me.firebaseUid(), legacyPublicId(firestoreId));
    }

    /** Pet do Firestore deste tutor, lido na hora. Outro dono ou pet inexistente: 404. */
    public FirestorePet ownedFirestorePet(AuthenticatedUser me, String firestoreId) {
        FirestorePet pet;
        try {
            pet = firestore.find(firestoreId).orElse(null);
        } catch (FirestorePets.Unavailable e) {
            log.warn("Firestore indisponível ao conferir o dono de um pet: {}", e.getMessage());
            throw ApiException.unavailable("Não foi possível confirmar o pet agora. Tente novamente em instantes.");
        }
        // Pet de outro tutor responde igual a pet inexistente.
        if (pet == null || pet.ownerUid() == null || !pet.ownerUid().equals(me.firebaseUid())) {
            throw petNotFound();
        }
        return pet;
    }

    /** Pet do MongoDB deste tutor. Outro dono, excluído ou inexistente: 404. */
    public Pet ownedApiPet(AuthenticatedUser me, String petId) {
        Pet pet = pets.findById(petId).orElse(null);
        if (pet == null || pet.getDeletedAt() != null || !me.userId().equals(pet.getTutorId())) {
            throw petNotFound();
        }
        return pet;
    }

    // ---------------------------------------------------------- público (sem auth)

    /**
     * O que a página do QR pode mostrar. Campos {@code null} não saem no JSON.
     * {@code photos} é o carrossel (perfil e as duas da galeria), só quando as
     * três estão disponíveis; senão a página mostra só {@code photoUrl}.
     */
    public record PublicPetView(boolean shared, String name, String species, String breed, String color,
                                String photoUrl, List<String> photos, String tutorName, String contactPhone) {

        public static final PublicPetView NEUTRAL =
                new PublicPetView(false, null, null, null, null, null, null, null, null);
    }

    /** Pet que um {@code publicId} resolve, para a página e para o relato de avistamento. */
    public record ResolvedPet(String apiPetId, String firestoreId) {

        /** Mesma chave de {@link PublicSharing#getPetKey()}. */
        public String petKey() {
            return firestoreId != null ? PublicSharing.firestoreKey(firestoreId) : PublicSharing.apiKey(apiPetId);
        }
    }

    public PublicPetView publicView(String publicId) {
        Resolution r = resolve(publicId);
        PublicSharing s = r.sharing();
        boolean shared = s != null && s.isActive(CONSENT_VERSION);

        if (r.firestoreId() == null) {
            Pet p = r.apiPet();
            return shared
                    ? view(s, p.getName(), p.getSpecies() == null ? null : p.getSpecies().name(),
                            p.getBreed(), p.getColor(), p.getPhotoUrl(), null)
                    : PublicPetView.NEUTRAL;
        }

        FirestorePet live;
        try {
            live = firestore.find(r.firestoreId()).orElse(null);
        } catch (FirestorePets.Unavailable e) {
            log.warn("Firestore indisponível na página pública: {}", e.getMessage());
            // Sem publicação, o mínimo neutro não depende do Firestore.
            if (!shared && r.apiPet() != null) {
                return PublicPetView.NEUTRAL;
            }
            throw ApiException.unavailable("Não foi possível carregar as informações agora.");
        }
        if (live == null) {
            forgetDeletedPet(s);
            throw petNotFound();
        }
        if (live.deceased()) {
            throw petNotFound();
        }
        if (!shared || !s.getOwnerUid().equals(live.ownerUid())) {
            return PublicPetView.NEUTRAL;
        }
        return view(s, live.name(), live.species(), live.breed(), live.color(), live.photoUrl(),
                carousel(s, r.firestoreId(), live));
    }

    /**
     * Perfil, foto 2 e foto 3, lidas na hora. Se uma das fotos escolhidas saiu
     * da galeria (ou o Firestore não respondeu), não há carrossel: a página não
     * publica um carrossel incompleto e mostra só a foto de perfil.
     */
    private List<String> carousel(PublicSharing s, String firestoreId, FirestorePet live) {
        List<String> ids = s.getGalleryPhotoIds();
        if (!s.shows(PublicField.PROFILE_PHOTO) || !s.shows(PublicField.GALLERY_PHOTOS)
                || live.photoUrl() == null || ids.size() != GALLERY_PHOTO_COUNT) {
            return null;
        }
        List<GalleryPhoto> found;
        try {
            found = firestore.galleryPhotos(firestoreId, ids);
        } catch (FirestorePets.Unavailable e) {
            log.warn("Firestore indisponível ao ler a galeria da página pública: {}", e.getMessage());
            return null;
        }
        if (found.size() != GALLERY_PHOTO_COUNT
                || found.stream().anyMatch(p -> !live.ownerUid().equals(p.ownerUid()))) {
            return null;
        }
        List<String> photos = new ArrayList<>();
        photos.add(live.photoUrl());
        found.forEach(p -> photos.add(p.url()));
        return List.copyOf(photos);
    }

    /** Mesmas regras da página: pet excluído, no Memorial, arquivado ou com QR revogado responde 404. */
    public ResolvedPet resolveForSighting(String publicId) {
        Resolution r = resolve(publicId);
        if (r.firestoreId() != null) {
            try {
                FirestorePet live = firestore.find(r.firestoreId()).orElse(null);
                if (live == null) {
                    forgetDeletedPet(r.sharing());
                    throw petNotFound();
                }
                if (live.deceased()) {
                    throw petNotFound();
                }
            } catch (FirestorePets.Unavailable e) {
                log.warn("Firestore indisponível no relato de avistamento: {}", e.getMessage());
                if (r.apiPet() == null) {
                    throw ApiException.unavailable("Não foi possível enviar agora. Tente novamente em instantes.");
                }
            }
        }
        return new ResolvedPet(r.apiPet() == null ? null : r.apiPet().getId(), r.firestoreId());
    }

    private record Resolution(Pet apiPet, String firestoreId, PublicSharing sharing) {
    }

    /**
     * O tutor excluiu o pet no app (Firestore) e a configuração ficou aqui,
     * com telefone e nome de exibição. Apaga no primeiro acesso ao QR; a
     * exclusão da conta também apaga ({@code UserService.deleteAccount}).
     */
    private void forgetDeletedPet(PublicSharing s) {
        if (s != null) {
            sharings.delete(s);
            log.info("Configuração da página pública apagada: pet excluído no Firestore ({}).", s.getPetKey());
        }
    }

    private Resolution resolve(String publicId) {
        Pet pet = pets.findByPublicId(publicId).orElse(null);
        if (pet != null) {
            if (pet.getDeletedAt() != null
                    || pet.getStatus() == PetStatus.ARCHIVED
                    || pet.getStatus() == PetStatus.DECEASED) {
                throw petNotFound();
            }
            String firestoreId = pet.getLegacyFirestoreId();
            String key = firestoreId != null ? PublicSharing.firestoreKey(firestoreId) : PublicSharing.apiKey(pet.getId());
            return new Resolution(pet, firestoreId, sharings.findByPetKey(key).orElse(null));
        }
        // Pet do Firestore que ainda não chegou ao MongoDB: só pelo publicId
        // determinístico. Se o pet já está em `pets`, vale o publicId de lá
        // (que pode ter sido regenerado) e este fica revogado.
        PublicSharing s = sharings.findByPublicId(publicId).orElseThrow(PublicSharingService::petNotFound);
        if (s.getFirestoreId() == null || pets.findByLegacyFirestoreId(s.getFirestoreId()).isPresent()) {
            throw petNotFound();
        }
        return new Resolution(null, s.getFirestoreId(), s);
    }

    private static PublicPetView view(PublicSharing s, String name, String species, String breed, String color,
                                      String photoUrl, List<String> photos) {
        return new PublicPetView(true,
                s.shows(PublicField.PET_NAME) ? name : null,
                s.shows(PublicField.SPECIES) ? species : null,
                s.shows(PublicField.BREED) ? breed : null,
                s.shows(PublicField.COLOR) ? color : null,
                s.shows(PublicField.PROFILE_PHOTO) ? photoUrl : null,
                photos,
                s.shows(PublicField.TUTOR_NAME) ? s.getTutorDisplayName() : null,
                s.shows(PublicField.CONTACT_PHONE) ? s.getContactPhoneE164() : null);
    }

    /** Mesmo valor da migração ({@code LegacyMigrationService}) e do app ({@code legacy_public_id.dart}). */
    static String legacyPublicId(String firestoreId) {
        return UUID.nameUUIDFromBytes(("pet:" + firestoreId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static ApiException petNotFound() {
        return ApiException.notFound("Pet não encontrado.");
    }

    private static ApiException memorialConflict() {
        return new ApiException(HttpStatus.CONFLICT, "PET_IN_MEMORIAL", "Pet no Memorial não tem página pública.");
    }
}
