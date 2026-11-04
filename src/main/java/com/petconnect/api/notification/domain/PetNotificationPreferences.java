package com.petconnect.api.notification.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * O que o tutor quer receber sobre o QR de um pet (issue #36 do app),
 * coleção {@code pet_notification_preferences}. Sem documento, os dois
 * avisos ficam ligados.
 */
@Document("pet_notification_preferences")
public class PetNotificationPreferences {

    @Id
    private String id;

    /** {@code fs:<id do Firestore>} ou {@code api:<id do MongoDB>}. */
    @Indexed(unique = true)
    private String petKey;

    @Indexed
    private String ownerUid;

    private boolean notifyOnAccess = true;
    private boolean notifyOnSighting = true;
    private Instant updatedAt;

    public static PetNotificationPreferences defaults(String petKey, String ownerUid) {
        PetNotificationPreferences p = new PetNotificationPreferences();
        p.petKey = petKey;
        p.ownerUid = ownerUid;
        return p;
    }

    public void update(boolean notifyOnAccess, boolean notifyOnSighting, String ownerUid, Instant now) {
        this.notifyOnAccess = notifyOnAccess;
        this.notifyOnSighting = notifyOnSighting;
        this.ownerUid = ownerUid;
        this.updatedAt = now;
    }

    public String getPetKey() {
        return petKey;
    }

    public String getOwnerUid() {
        return ownerUid;
    }

    public boolean isNotifyOnAccess() {
        return notifyOnAccess;
    }

    public boolean isNotifyOnSighting() {
        return notifyOnSighting;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
