package com.petconnect.api.notification.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Aparelho do tutor que recebe push (FCM), coleção {@code push_devices}
 * (issue #36 do app). Um token pertence a um só usuário: se outra conta
 * entrar no mesmo aparelho, o token passa para ela.
 *
 * <p>O token é segredo do aparelho: nunca sai em log nem em resposta.
 */
@Document("push_devices")
public class PushDevice {

    @Id
    private String id;

    @Indexed(unique = true)
    private String token;

    @Indexed
    private String ownerUid;

    private String platform;
    private Instant createdAt;
    private Instant lastSeenAt;

    public static PushDevice register(String token, String ownerUid, String platform, Instant now) {
        PushDevice d = new PushDevice();
        d.token = token;
        d.ownerUid = ownerUid;
        d.platform = platform;
        d.createdAt = now;
        d.lastSeenAt = now;
        return d;
    }

    /** Mesmo aparelho de novo: atualiza o dono e a última vez visto. */
    public void seen(String ownerUid, String platform, Instant now) {
        this.ownerUid = ownerUid;
        this.platform = platform;
        this.lastSeenAt = now;
    }

    public String getId() {
        return id;
    }

    public String getToken() {
        return token;
    }

    public String getOwnerUid() {
        return ownerUid;
    }

    public String getPlatform() {
        return platform;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }
}
