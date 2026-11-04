package com.petconnect.api.notification.application;

import com.petconnect.api.notification.domain.PetNotificationPreferences;
import com.petconnect.api.notification.domain.PushDevice;
import com.petconnect.api.notification.infrastructure.PetNotificationPreferencesRepository;
import com.petconnect.api.notification.infrastructure.PushDeviceRepository;
import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.shared.security.AuthenticatedUser;
import com.petconnect.api.sharing.application.PublicSharingService;
import com.petconnect.api.sharing.domain.PublicSharing;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Aparelhos do tutor e o que ele quer receber de cada pet (issue #36 do app). */
@Service
public class NotificationSettingsService {

    private final PushDeviceRepository devices;
    private final PetNotificationPreferencesRepository preferences;
    private final PublicSharingService sharing;

    public NotificationSettingsService(PushDeviceRepository devices, PetNotificationPreferencesRepository preferences,
                                       PublicSharingService sharing) {
        this.devices = devices;
        this.preferences = preferences;
        this.sharing = sharing;
    }

    // ---------------------------------------------------------- aparelhos

    public void registerDevice(AuthenticatedUser me, String token, String platform) {
        Instant now = Instant.now();
        PushDevice device = devices.findByToken(token)
                .map(d -> {
                    d.seen(me.firebaseUid(), platform, now);
                    return d;
                })
                .orElseGet(() -> PushDevice.register(token, me.firebaseUid(), platform, now));
        try {
            devices.save(device);
        } catch (DuplicateKeyException registeredAtTheSameTime) {
            devices.findByToken(token).ifPresent(d -> {
                d.seen(me.firebaseUid(), platform, now);
                devices.save(d);
            });
        }
    }

    /** Só apaga o token se ele for deste usuário. */
    public void unregisterDevice(AuthenticatedUser me, String token) {
        devices.deleteByTokenAndOwnerUid(token, me.firebaseUid());
    }

    // ---------------------------------------------------------- preferências

    public PetNotificationPreferences forFirestorePet(AuthenticatedUser me, String firestoreId) {
        sharing.ownedFirestorePet(me, firestoreId);
        return current(PublicSharing.firestoreKey(firestoreId), me);
    }

    public PetNotificationPreferences updateForFirestorePet(AuthenticatedUser me, String firestoreId,
                                                            boolean onAccess, boolean onSighting) {
        sharing.ownedFirestorePet(me, firestoreId);
        return update(PublicSharing.firestoreKey(firestoreId), me, onAccess, onSighting);
    }

    public PetNotificationPreferences forApiPet(AuthenticatedUser me, String petId) {
        return current(keyOf(sharing.ownedApiPet(me, petId)), me);
    }

    public PetNotificationPreferences updateForApiPet(AuthenticatedUser me, String petId,
                                                      boolean onAccess, boolean onSighting) {
        return update(keyOf(sharing.ownedApiPet(me, petId)), me, onAccess, onSighting);
    }

    /** Mesma chave que a página pública usa ao avisar ({@code ResolvedPet.petKey}). */
    private static String keyOf(Pet pet) {
        return pet.getLegacyFirestoreId() != null
                ? PublicSharing.firestoreKey(pet.getLegacyFirestoreId())
                : PublicSharing.apiKey(pet.getId());
    }

    private PetNotificationPreferences current(String petKey, AuthenticatedUser me) {
        return preferences.findByPetKey(petKey)
                .orElseGet(() -> PetNotificationPreferences.defaults(petKey, me.firebaseUid()));
    }

    private PetNotificationPreferences update(String petKey, AuthenticatedUser me, boolean onAccess,
                                              boolean onSighting) {
        PetNotificationPreferences p = current(petKey, me);
        p.update(onAccess, onSighting, me.firebaseUid(), Instant.now());
        return preferences.save(p);
    }
}
