package com.petconnect.api.privacy.application;

import com.petconnect.api.location.infrastructure.LocationRepository;
import com.petconnect.api.notification.infrastructure.PetNotificationPreferencesRepository;
import com.petconnect.api.notification.infrastructure.TutorNotificationRepository;
import com.petconnect.api.qractivity.infrastructure.QrAccessEventRepository;
import com.petconnect.api.sharing.domain.PublicSharing;
import com.petconnect.api.sharing.infrastructure.PublicSharingRepository;
import org.springframework.stereotype.Service;

/**
 * Apaga o que a API guarda sobre o QR de um pet que deixou de existir
 * (LGPD, eliminação dos dados ao fim do tratamento). Um único lugar para
 * todos os caminhos de exclusão: a rota do tutor para pets do Firestore
 * ({@link QrDataErasureService}), a exclusão de pet da API
 * ({@code PetService.delete}), a limpeza no acesso ao QR de um pet já
 * excluído ({@code PublicSharingService}) e a exclusão de conta
 * ({@code UserService.deleteAccount}).
 *
 * <p>Por {@code petKey} ({@code fs:<id do Firestore>} ou {@code api:<id>}):
 * <ul>
 *   <li>configuração da página pública ({@code pet_public_sharing}), com o
 *       telefone e o nome de exibição do tutor;</li>
 *   <li>acessos à página ({@code qr_access_events});</li>
 *   <li>preferências de aviso ({@code pet_notification_preferences});</li>
 *   <li>registros de aviso ao tutor ({@code tutor_notifications});</li>
 *   <li>relatos e localizações ({@code locations}): pelo id do Firestore
 *       ({@code legacyPetId}) ou pelo {@code _id} do pet da API, com a
 *       coordenada e o contato de quem achou.</li>
 * </ul>
 *
 * <p>Fica de propósito o histórico de consentimento
 * ({@code public_sharing_consent_events}): só registra quando o tutor
 * aceitou, alterou ou revogou e quais campos, sem telefone nem nome, e serve
 * de prova do consentimento. O prazo de guarda desse histórico ainda não foi
 * definido (docs/privacy do app).
 *
 * <p>Idempotente: rodar de novo não acha nada e não falha.
 */
@Service
public class PetQrDataEraser {

    private static final String FIRESTORE_PREFIX = PublicSharing.firestoreKey("");
    private static final String API_PREFIX = PublicSharing.apiKey("");

    private final PublicSharingRepository sharings;
    private final QrAccessEventRepository accesses;
    private final LocationRepository locations;
    private final PetNotificationPreferencesRepository preferences;
    private final TutorNotificationRepository notifications;

    public PetQrDataEraser(PublicSharingRepository sharings, QrAccessEventRepository accesses,
                           LocationRepository locations, PetNotificationPreferencesRepository preferences,
                           TutorNotificationRepository notifications) {
        this.sharings = sharings;
        this.accesses = accesses;
        this.locations = locations;
        this.preferences = preferences;
        this.notifications = notifications;
    }

    /** Quantos documentos saíram de cada coleção. Só contagens: nada do conteúdo. */
    public record Erased(long sharings, long accesses, long locations, long preferences, long notifications) {

        public static final Erased NONE = new Erased(0, 0, 0, 0, 0);

        public long total() {
            return sharings + accesses + locations + preferences + notifications;
        }
    }

    /** Pet do app em modo Firestore: chave {@code fs:<id>} e relatos por {@code legacyPetId}. */
    public Erased eraseFirestorePet(String firestoreId) {
        String key = PublicSharing.firestoreKey(firestoreId);
        return byKey(key, locations.deleteByLegacyPetId(firestoreId));
    }

    /** Pet criado pela API: chave {@code api:<id>} e relatos por {@code petId}. */
    public Erased eraseApiPet(String apiPetId) {
        String key = PublicSharing.apiKey(apiPetId);
        return byKey(key, locations.deleteByPetId(apiPetId));
    }

    /**
     * Pela chave já montada ({@code fs:…} ou {@code api:…}), como guardada
     * nas coleções. Chave em outro formato não apaga nada.
     */
    public Erased erasePetKey(String petKey) {
        if (petKey.equals(FIRESTORE_PREFIX) || petKey.equals(API_PREFIX)) {
            return Erased.NONE;
        }
        if (petKey.startsWith(FIRESTORE_PREFIX)) {
            return eraseFirestorePet(petKey.substring(FIRESTORE_PREFIX.length()));
        }
        if (petKey.startsWith(API_PREFIX)) {
            return eraseApiPet(petKey.substring(API_PREFIX.length()));
        }
        return Erased.NONE;
    }

    private Erased byKey(String petKey, long locationsDeleted) {
        return new Erased(
                sharings.deleteByPetKey(petKey),
                accesses.deleteByPetKey(petKey),
                locationsDeleted,
                preferences.deleteByPetKey(petKey),
                notifications.deleteByPetKey(petKey));
    }
}
