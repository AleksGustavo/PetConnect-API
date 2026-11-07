package com.petconnect.api.privacy.application;

import com.petconnect.api.notification.infrastructure.PetNotificationPreferencesRepository;
import com.petconnect.api.pet.application.PetService;
import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.pet.infrastructure.PetRepository;
import com.petconnect.api.privacy.application.PetQrDataEraser.Erased;
import com.petconnect.api.shared.error.ApiException;
import com.petconnect.api.shared.security.AuthenticatedUser;
import com.petconnect.api.sharing.application.FirestorePets;
import com.petconnect.api.sharing.application.FirestorePets.FirestorePet;
import com.petconnect.api.sharing.domain.PublicSharing;
import com.petconnect.api.sharing.infrastructure.PublicSharingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * O tutor vai excluir no app um pet que está no Firestore e pede para a API
 * apagar o que guarda dele ({@code DELETE /legacy-pets/{id}/qr-data}): o que
 * {@link PetQrDataEraser} apaga e, se a migração já tinha levado o pet ao
 * MongoDB, essa cópia com vacinas, consultas, histórico e localizações
 * ({@code PetService.delete}).
 *
 * <p>Quem pode:
 * <ul>
 *   <li><b>Pet ainda no Firestore</b> (o app chama antes de excluir): só o
 *       dono, conferido lá na hora, como nas outras rotas {@code legacy-pets}.
 *       Outro dono responde 404, igual a pet inexistente.</li>
 *   <li><b>Pet já excluído do Firestore</b> (nova tentativa do app depois de
 *       a API ter ficado fora no momento da exclusão): não há mais dono para
 *       conferir lá. Apaga se nada do que ficou aqui aponta outro dono; se
 *       algo aponta, 404. É o mesmo que o próximo acesso ao QR desse pet já
 *       faria sem login ({@code PublicSharingService}).</li>
 * </ul>
 *
 * <p>Firestore fora do ar: 503 e nada é apagado (sem dono confirmado).
 */
@Service
public class QrDataErasureService {

    private static final Logger log = LoggerFactory.getLogger(QrDataErasureService.class);

    private final FirestorePets firestore;
    private final PetRepository pets;
    private final PetService petService;
    private final PublicSharingRepository sharings;
    private final PetNotificationPreferencesRepository preferences;
    private final PetQrDataEraser eraser;

    public QrDataErasureService(FirestorePets firestore, PetRepository pets, PetService petService,
                                PublicSharingRepository sharings, PetNotificationPreferencesRepository preferences,
                                PetQrDataEraser eraser) {
        this.firestore = firestore;
        this.pets = pets;
        this.petService = petService;
        this.sharings = sharings;
        this.preferences = preferences;
        this.eraser = eraser;
    }

    public Erased eraseForFirestorePet(AuthenticatedUser me, String firestoreId) {
        Optional<FirestorePet> live;
        try {
            live = firestore.find(firestoreId);
        } catch (FirestorePets.Unavailable e) {
            log.warn("Firestore indisponível ao apagar os dados do QR: {}", e.getMessage());
            throw ApiException.unavailable("Não foi possível confirmar o pet agora. Tente novamente em instantes.");
        }

        Pet copy = pets.findByLegacyFirestoreId(firestoreId).orElse(null);
        if (live.isPresent()) {
            if (!me.firebaseUid().equals(live.get().ownerUid())) {
                throw petNotFound();
            }
        } else if (belongsToSomeoneElse(me, firestoreId, copy)) {
            throw petNotFound();
        }

        boolean copyDeleted = false;
        if (copy != null) {
            if (me.userId().equals(copy.getTutorId())) {
                petService.delete(me.userId(), copy.getId());
                copyDeleted = true;
            } else {
                // Firestore e MongoDB discordam do dono: a cópia fica para a
                // próxima migração resolver. Sem o id do tutor no log.
                log.warn("Cópia migrada de pet do Firestore com outro tutor no MongoDB; mantida ({}).",
                        PublicSharing.firestoreKey(firestoreId));
            }
        }
        // Depois da cópia: o que só existia pelo id do Firestore.
        Erased erased = eraser.eraseFirestorePet(firestoreId);
        log.info("Dados do QR apagados a pedido do tutor ({}): {} documento(s){}.",
                PublicSharing.firestoreKey(firestoreId), erased.total(),
                copyDeleted ? " e a cópia migrada" : "");
        return erased;
    }

    /** Pet já fora do Firestore: algum registro daqui diz que era de outra pessoa? */
    private boolean belongsToSomeoneElse(AuthenticatedUser me, String firestoreId, Pet copy) {
        String key = PublicSharing.firestoreKey(firestoreId);
        boolean otherSharingOwner = sharings.findByPetKey(key)
                .map(PublicSharing::getOwnerUid)
                .filter(uid -> !uid.equals(me.firebaseUid()))
                .isPresent();
        boolean otherPreferencesOwner = preferences.findByPetKey(key)
                .map(p -> p.getOwnerUid())
                .filter(uid -> uid != null && !uid.equals(me.firebaseUid()))
                .isPresent();
        boolean otherCopyOwner = copy != null && !me.userId().equals(copy.getTutorId());
        return otherSharingOwner || otherPreferencesOwner || otherCopyOwner;
    }

    private static ApiException petNotFound() {
        return ApiException.notFound("Pet não encontrado.");
    }
}
