package com.petconnect.api.notification.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.Instant;

/**
 * Um aviso ao tutor sobre o QR de um pet e o que aconteceu com ele
 * (issue #36 do app), coleção {@code tutor_notifications}. Serve para
 * limitar a frequência e para investigar entregas; não guarda token, texto
 * do relato nem dado de quem relatou. Apagado sozinho {@link #RETENTION}
 * depois.
 */
@Document("tutor_notifications")
@CompoundIndex(name = "pet_type_time", def = "{'petKey': 1, 'type': 1, 'createdAt': -1}")
public class TutorNotification {

    public static final Duration RETENTION = Duration.ofDays(30);

    public enum Type {
        /** {@code qr_page_opened}: a página do QR foi aberta. Não é avistamento. */
        QR_ACCESS,
        /** {@code sighting_location_shared} ou relato em texto. */
        SIGHTING
    }

    public enum Status {
        PENDING,
        SENT,
        FAILED,
        /** O tutor não tem aparelho registrado (app sem login ou sem permissão). */
        NO_DEVICE
    }

    @Id
    private String id;
    private String petKey;

    @Indexed
    private String ownerUid;

    private Type type;
    private Status status = Status.PENDING;
    private int attempts;
    private int devicesReached;
    private String lastError;
    private Instant createdAt;
    private Instant finishedAt;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    public static TutorNotification pending(String petKey, String ownerUid, Type type, Instant now) {
        TutorNotification n = new TutorNotification();
        n.petKey = petKey;
        n.ownerUid = ownerUid;
        n.type = type;
        n.createdAt = now;
        n.expiresAt = now.plus(RETENTION);
        return n;
    }

    public void finish(Status status, int attempts, int devicesReached, String lastError, Instant now) {
        this.status = status;
        this.attempts = attempts;
        this.devicesReached = devicesReached;
        this.lastError = lastError;
        this.finishedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getPetKey() {
        return petKey;
    }

    public String getOwnerUid() {
        return ownerUid;
    }

    public Type getType() {
        return type;
    }

    public Status getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getDevicesReached() {
        return devicesReached;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
