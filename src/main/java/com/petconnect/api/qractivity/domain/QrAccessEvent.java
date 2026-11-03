package com.petconnect.api.qractivity.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Duration;
import java.time.Instant;

/**
 * {@code qr_page_opened}: a página pública do QR carregou um pet válido
 * (issue #37 do app). Não é avistamento e não tem coordenada: abrir o link
 * não prova que alguém está com o pet, e pré-visualizações de leitores de QR
 * também abrem a página.
 *
 * <p>Coleção {@code qr_access_events}. Sem IP, user-agent ou qualquer dado
 * de quem abriu. Apagado sozinho {@link #RETENTION} depois (índice TTL).
 */
@Document("qr_access_events")
@CompoundIndex(name = "pet_time", def = "{'petKey': 1, 'occurredAt': -1}")
public class QrAccessEvent {

    public static final Duration RETENTION = Duration.ofDays(90);

    @Id
    private String id;

    /** {@code fs:<id do Firestore>} ou {@code api:<id do MongoDB>}, como em {@code pet_public_sharing}. */
    private String petKey;
    private String apiPetId;
    private String firestoreId;
    private Instant occurredAt;

    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    public static QrAccessEvent pageOpened(String petKey, String apiPetId, String firestoreId, Instant now) {
        QrAccessEvent e = new QrAccessEvent();
        e.petKey = petKey;
        e.apiPetId = apiPetId;
        e.firestoreId = firestoreId;
        e.occurredAt = now;
        e.expiresAt = now.plus(RETENTION);
        return e;
    }

    public String getId() {
        return id;
    }

    public String getPetKey() {
        return petKey;
    }

    public String getApiPetId() {
        return apiPetId;
    }

    public String getFirestoreId() {
        return firestoreId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
