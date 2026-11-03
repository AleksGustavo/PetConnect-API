package com.petconnect.api.sharing.application;

import java.util.Optional;

/**
 * Leitura de um pet na coleção {@code Pets} do Firestore, a fonte dos pets do
 * app em modo Firestore (o padrão hoje). Só leitura: a API nunca escreve no
 * Firestore.
 *
 * <p>Existe para dois casos da página pública (issue #35 do app): conferir
 * quem é o dono de um pet que ainda não chegou ao MongoDB pela migração e
 * mostrar os dados atuais do pet, em vez de uma cópia desatualizada.
 */
public interface FirestorePets {

    /**
     * @return vazio se o documento não existe (pet excluído ou id inventado)
     * @throws Unavailable se o Firestore não respondeu ou a credencial não tem acesso
     */
    Optional<FirestorePet> find(String firestoreId);

    /** Dados de {@code Pets/{id}} que a API usa. Nada clínico. */
    record FirestorePet(String ownerUid, String name, String species, String breed, String color,
                        String photoUrl, boolean deceased) {
    }

    class Unavailable extends RuntimeException {
        public Unavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
