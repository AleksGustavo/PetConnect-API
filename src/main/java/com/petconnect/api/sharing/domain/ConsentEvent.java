package com.petconnect.api.sharing.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * Histórico do consentimento da página pública (coleção
 * {@code public_sharing_consent_events}). Só inclusão: nenhuma rota altera ou
 * apaga. Guarda o escopo (quais campos), não os valores: o telefone e o nome
 * de exibição não são copiados para o histórico.
 */
@Document(collection = "public_sharing_consent_events")
public class ConsentEvent {

    public enum Type {
        ACCEPTED,
        UPDATED,
        REVOKED
    }

    @Id
    private String id;

    @Indexed
    private String petKey;
    private Type type;
    private Set<PublicField> fields = EnumSet.noneOf(PublicField.class);
    private String consentVersion;
    private String actorUid;
    private Instant at;

    public static ConsentEvent of(String petKey, Type type, Set<PublicField> fields,
                                  String consentVersion, String actorUid, Instant at) {
        ConsentEvent e = new ConsentEvent();
        e.petKey = petKey;
        e.type = type;
        e.fields = fields.isEmpty() ? EnumSet.noneOf(PublicField.class) : EnumSet.copyOf(fields);
        e.consentVersion = consentVersion;
        e.actorUid = actorUid;
        e.at = at;
        return e;
    }

    public String getId() {
        return id;
    }

    public String getPetKey() {
        return petKey;
    }

    public Type getType() {
        return type;
    }

    public Set<PublicField> getFields() {
        return fields;
    }

    public String getConsentVersion() {
        return consentVersion;
    }

    public String getActorUid() {
        return actorUid;
    }

    public Instant getAt() {
        return at;
    }
}
