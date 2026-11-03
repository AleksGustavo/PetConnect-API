package com.petconnect.api.sharing.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * O que o tutor autorizou publicar na página do QR de um pet. Coleção
 * {@code pet_public_sharing}.
 *
 * <p>Fica fora de {@code pets} de propósito: a migração apaga e recria os
 * pets importados do Firestore, e o consentimento não pode sumir junto. A
 * chave é o id do Firestore ({@code fs:<id>}) para pets do app em modo
 * Firestore e o {@code _id} ({@code api:<id>}) para os criados pela API.
 *
 * <p>Os dados do pet (nome, foto…) não são copiados para cá: a resposta
 * pública lê o pet na fonte a cada acesso. Aqui ficam só o que não existe em
 * outro lugar: o nome de exibição do tutor e o telefone de contato que ele
 * confirmou para a página.
 */
@Document(collection = "pet_public_sharing")
public class PublicSharing {

    @Id
    private String id;

    @Indexed(unique = true)
    private String petKey;

    /** Id do documento em {@code Pets} do Firestore; {@code null} para pet da API. */
    private String firestoreId;
    /** {@code _id} em {@code pets}; {@code null} para pet do Firestore. */
    private String apiPetId;
    /** Firebase UID do tutor que gerencia o compartilhamento. */
    private String ownerUid;

    /**
     * {@code publicId} determinístico do pet do Firestore (o mesmo da
     * migração e do app). Resolve o QR de um pet que ainda não está em
     * {@code pets}; quando o pet existe lá, vale o {@code publicId} de lá.
     */
    @Indexed(unique = true, sparse = true)
    private String publicId;

    private boolean enabled;
    private Set<PublicField> fields = EnumSet.noneOf(PublicField.class);
    private String tutorDisplayName;
    private String contactPhoneE164;

    private Instant consentAcceptedAt;
    private String consentVersion;
    private String consentAcceptedBy;
    private Instant consentRevokedAt;

    @CreatedDate
    private Instant createdAt;
    @LastModifiedDate
    private Instant updatedAt;

    public static String firestoreKey(String firestoreId) {
        return "fs:" + firestoreId;
    }

    public static String apiKey(String petId) {
        return "api:" + petId;
    }

    public static PublicSharing forFirestorePet(String firestoreId, String ownerUid, String publicId) {
        PublicSharing s = new PublicSharing();
        s.petKey = firestoreKey(firestoreId);
        s.firestoreId = firestoreId;
        s.ownerUid = ownerUid;
        s.publicId = publicId;
        return s;
    }

    public static PublicSharing forApiPet(String petId, String ownerUid) {
        PublicSharing s = new PublicSharing();
        s.petKey = apiKey(petId);
        s.apiPetId = petId;
        s.ownerUid = ownerUid;
        return s;
    }

    /** Publicação vale só com consentimento aceito, não revogado, na versão do texto informada. */
    public boolean isActive(String currentConsentVersion) {
        return enabled
                && consentAcceptedAt != null
                && consentRevokedAt == null
                && currentConsentVersion.equals(consentVersion)
                && !fields.isEmpty();
    }

    public boolean shows(PublicField field) {
        return fields.contains(field);
    }

    public void publish(Set<PublicField> fields, String tutorDisplayName, String contactPhoneE164,
                        String consentVersion, String actorUid, Instant now) {
        this.enabled = true;
        this.fields = fields.isEmpty() ? EnumSet.noneOf(PublicField.class) : EnumSet.copyOf(fields);
        this.tutorDisplayName = tutorDisplayName;
        this.contactPhoneE164 = contactPhoneE164;
        this.consentAcceptedAt = now;
        this.consentVersion = consentVersion;
        this.consentAcceptedBy = actorUid;
        this.consentRevokedAt = null;
    }

    /** Desativa e apaga o que só existia para a página (nome de exibição e telefone). */
    public void revoke(Instant now) {
        if (consentAcceptedAt != null && consentRevokedAt == null) {
            this.consentRevokedAt = now;
        }
        this.enabled = false;
        this.fields = EnumSet.noneOf(PublicField.class);
        this.tutorDisplayName = null;
        this.contactPhoneE164 = null;
    }

    public String getId() {
        return id;
    }

    public String getPetKey() {
        return petKey;
    }

    public String getFirestoreId() {
        return firestoreId;
    }

    public String getApiPetId() {
        return apiPetId;
    }

    public String getOwnerUid() {
        return ownerUid;
    }

    public String getPublicId() {
        return publicId;
    }

    public void setPublicId(String publicId) {
        this.publicId = publicId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<PublicField> getFields() {
        return fields;
    }

    public String getTutorDisplayName() {
        return tutorDisplayName;
    }

    public String getContactPhoneE164() {
        return contactPhoneE164;
    }

    public Instant getConsentAcceptedAt() {
        return consentAcceptedAt;
    }

    public String getConsentVersion() {
        return consentVersion;
    }

    public String getConsentAcceptedBy() {
        return consentAcceptedBy;
    }

    public Instant getConsentRevokedAt() {
        return consentRevokedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
