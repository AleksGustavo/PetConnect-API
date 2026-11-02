package com.petconnect.api.sharing.infrastructure;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.FirestoreClient;
import com.petconnect.api.migration.support.LegacyValues;
import com.petconnect.api.migration.support.PhotoResolver;
import com.petconnect.api.sharing.application.FirestorePets;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * {@link FirestorePets} pelo Firebase Admin SDK, com a mesma credencial que
 * valida os tokens ({@code FIREBASE_SERVICE_ACCOUNT}). A conta de serviço
 * precisa de leitura no Firestore (papel {@code roles/datastore.viewer} ou
 * superior); sem isso, toda leitura vira {@link Unavailable}.
 */
@Component
class FirestorePetsAdmin implements FirestorePets {

    /** Mesmo nome do app criado em {@code FirebaseConfig}. */
    private static final String APP_NAME = "petconnect";
    private static final long TIMEOUT_SECONDS = 5;

    @Override
    public Optional<FirestorePet> find(String firestoreId) {
        DocumentSnapshot doc;
        try {
            FirebaseApp app = FirebaseApp.getInstance(APP_NAME);
            doc = FirestoreClient.getFirestore(app)
                    .collection("Pets").document(firestoreId)
                    .get().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Unavailable("Leitura do Firestore interrompida.", e);
        } catch (Exception e) {
            // Sem credencial (app não inicializado), sem permissão ou fora do ar.
            throw new Unavailable("Firestore indisponível para a API.", e);
        }
        if (!doc.exists()) {
            return Optional.empty();
        }
        String owner = text(doc, "userId");
        if (owner == null) {
            owner = text(doc, "userID");
        }
        String photo = PhotoResolver.resolve(
                text(doc, "foto"), text(doc, "imagemUrl"), text(doc, "imageUrl"), text(doc, "imagemUrlPet")).url();
        return Optional.of(new FirestorePet(
                owner,
                text(doc, "nome"),
                text(doc, "especie"),
                text(doc, "raca"),
                text(doc, "cor"),
                photo,
                "FALECIDO".equals(text(doc, "situacao"))));
    }

    private static String text(DocumentSnapshot doc, String field) {
        Object value = doc.get(field);
        return value instanceof String s ? LegacyValues.trimToNull(s) : null;
    }
}
