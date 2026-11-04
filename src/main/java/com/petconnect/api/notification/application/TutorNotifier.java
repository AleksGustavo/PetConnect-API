package com.petconnect.api.notification.application;

import com.petconnect.api.notification.application.PushSender.PushMessage;
import com.petconnect.api.notification.application.PushSender.Result;
import com.petconnect.api.notification.domain.PetNotificationPreferences;
import com.petconnect.api.notification.domain.PushDevice;
import com.petconnect.api.notification.domain.TutorNotification;
import com.petconnect.api.notification.domain.TutorNotification.Status;
import com.petconnect.api.notification.domain.TutorNotification.Type;
import com.petconnect.api.notification.infrastructure.PetNotificationPreferencesRepository;
import com.petconnect.api.notification.infrastructure.PushDeviceRepository;
import com.petconnect.api.notification.infrastructure.TutorNotificationRepository;
import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.pet.infrastructure.PetRepository;
import com.petconnect.api.sharing.application.FirestorePets;
import com.petconnect.api.sharing.application.FirestorePets.FirestorePet;
import com.petconnect.api.sharing.application.PublicSharingService.ResolvedPet;
import com.petconnect.api.user.domain.User;
import com.petconnect.api.user.infrastructure.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Avisa o tutor quando a página do QR do pet é aberta ou recebe um relato
 * (issue #36 do app, fase 4 do épico #102).
 *
 * <p>Roda fora da requisição ({@link NotificationConfig}): a página pública
 * nunca espera o push. Não há garantia de entrega nem de tempo: o push
 * depende do FCM, da rede e das configurações do aparelho.
 *
 * <ul>
 *   <li><b>Acesso</b>: no máximo um aviso por pet a cada {@link #ACCESS_QUIET_PERIOD};
 *       o texto diz que a página foi acessada, nunca que o pet foi visto.</li>
 *   <li><b>Avistamento</b>: sempre avisa; o limite de relatos por pet já freia spam.</li>
 * </ul>
 *
 * <p>O payload leva só o nome do pet e os ids para abrir a tela certa: nada
 * do relato, do contato de quem relatou ou da coordenada.
 */
@Service
public class TutorNotifier {

    private static final Logger log = LoggerFactory.getLogger(TutorNotifier.class);

    static final Duration ACCESS_QUIET_PERIOD = Duration.ofMinutes(15);
    static final int MAX_ATTEMPTS = 2;
    private static final Duration RETRY_DELAY = Duration.ofMillis(500);

    private final PushSender sender;
    private final PushDeviceRepository devices;
    private final PetNotificationPreferencesRepository preferences;
    private final TutorNotificationRepository notifications;
    private final FirestorePets firestore;
    private final PetRepository pets;
    private final UserRepository users;
    private final NotificationConfig.NotificationExecutor executor;

    public TutorNotifier(PushSender sender, PushDeviceRepository devices,
                         PetNotificationPreferencesRepository preferences, TutorNotificationRepository notifications,
                         FirestorePets firestore, PetRepository pets, UserRepository users,
                         NotificationConfig.NotificationExecutor executor) {
        this.sender = sender;
        this.devices = devices;
        this.preferences = preferences;
        this.notifications = notifications;
        this.firestore = firestore;
        this.pets = pets;
        this.users = users;
        this.executor = executor;
    }

    public void qrPageOpened(ResolvedPet pet) {
        dispatch(pet, Type.QR_ACCESS, false);
    }

    public void sightingReported(ResolvedPet pet, boolean withLocation) {
        dispatch(pet, Type.SIGHTING, withLocation);
    }

    private void dispatch(ResolvedPet pet, Type type, boolean withLocation) {
        executor.execute(() -> {
            try {
                deliver(pet, type, withLocation);
            } catch (RuntimeException e) {
                log.warn("Aviso ao tutor não enviado ({}, {}): {}.", pet.petKey(), type, e.getClass().getSimpleName());
            }
        });
    }

    private void deliver(ResolvedPet pet, Type type, boolean withLocation) {
        Owner owner = owner(pet);
        if (owner == null) {
            return;
        }
        PetNotificationPreferences prefs = preferences.findByPetKey(pet.petKey())
                .orElseGet(() -> PetNotificationPreferences.defaults(pet.petKey(), owner.uid()));
        if (type == Type.QR_ACCESS && !prefs.isNotifyOnAccess()
                || type == Type.SIGHTING && !prefs.isNotifyOnSighting()) {
            return;
        }
        Instant now = Instant.now();
        if (type == Type.QR_ACCESS && notifications.existsByPetKeyAndTypeAndCreatedAtAfter(
                pet.petKey(), Type.QR_ACCESS, now.minus(ACCESS_QUIET_PERIOD))) {
            return;
        }

        TutorNotification record = notifications.save(TutorNotification.pending(pet.petKey(), owner.uid(), type, now));
        List<PushDevice> targets = devices.findByOwnerUid(owner.uid());
        if (targets.isEmpty()) {
            record.finish(Status.NO_DEVICE, 0, 0, null, Instant.now());
            notifications.save(record);
            return;
        }

        PushMessage message = message(pet, type, owner.petName(), withLocation);
        int attempts = 0;
        int reached = 0;
        String lastError = null;
        for (PushDevice device : targets) {
            Result result = Result.RETRYABLE;
            for (int i = 0; i < MAX_ATTEMPTS && result == Result.RETRYABLE; i++) {
                if (i > 0) {
                    pause();
                }
                attempts++;
                result = sender.send(device.getToken(), message);
            }
            switch (result) {
                case SENT -> reached++;
                case INVALID_TOKEN -> {
                    devices.deleteByToken(device.getToken());
                    lastError = "INVALID_TOKEN";
                }
                default -> lastError = result.name();
            }
        }
        record.finish(reached > 0 ? Status.SENT : Status.FAILED, attempts, reached, lastError, Instant.now());
        notifications.save(record);
    }

    static PushMessage message(ResolvedPet pet, Type type, String petName, boolean withLocation) {
        String name = petName == null || petName.isBlank() ? "seu pet" : petName.strip();
        Map<String, String> data = new HashMap<>();
        data.put("type", type == Type.QR_ACCESS ? "qr_access" : "sighting");
        if (pet.firestoreId() != null) {
            data.put("firestorePetId", pet.firestoreId());
        }
        if (pet.apiPetId() != null) {
            data.put("apiPetId", pet.apiPetId());
        }
        String collapseKey = (type == Type.QR_ACCESS ? "qr-access-" : "sighting-") + pet.petKey();
        if (type == Type.QR_ACCESS) {
            return new PushMessage("O QR Code de " + name + " foi acessado",
                    "Alguém abriu a página do QR. Isso não confirma que o pet foi visto.", data, collapseKey);
        }
        return new PushMessage("Novo avistamento de " + name,
                withLocation
                        ? "Quem encontrou enviou a localização. Toque para ver no app."
                        : "Quem encontrou enviou um relato. Toque para ver no app.",
                data, collapseKey);
    }

    private record Owner(String uid, String petName) {
    }

    /** Dono atual do pet: do Firestore, lido na hora, ou do MongoDB. */
    private Owner owner(ResolvedPet pet) {
        if (pet.firestoreId() != null) {
            try {
                FirestorePet live = firestore.find(pet.firestoreId()).orElse(null);
                if (live != null && live.ownerUid() != null) {
                    return new Owner(live.ownerUid(), live.name());
                }
            } catch (FirestorePets.Unavailable e) {
                log.warn("Firestore indisponível ao avisar o tutor: {}", e.getMessage());
            }
        }
        if (pet.apiPetId() == null) {
            return null;
        }
        Pet p = pets.findById(pet.apiPetId()).orElse(null);
        if (p == null || p.getTutorId() == null) {
            return null;
        }
        return users.findById(p.getTutorId())
                .map(User::getFirebaseUid)
                .map(uid -> new Owner(uid, p.getName()))
                .orElse(null);
    }

    private static void pause() {
        try {
            Thread.sleep(RETRY_DELAY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
