package com.petconnect.api.sharing.infrastructure;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.cloud.FirestoreClient;
import com.petconnect.api.migration.support.LegacyValues;
import com.petconnect.api.migration.support.PhotoResolver;
import com.petconnect.api.sharing.application.FirestorePets;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    /** Só arquivos do Cloudinary vão para a página, como a foto de perfil ({@link PhotoResolver}). */
    private static final String CLOUDINARY_HTTPS = "https://res.cloudinary.com/";

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

    @Override
    public List<GalleryPhoto> galleryPhotos(String firestoreId, List<String> photoIds) {
        if (photoIds.isEmpty()) {
            return List.of();
        }
        List<DocumentSnapshot> docs;
        try {
            Firestore db = FirestoreClient.getFirestore(FirebaseApp.getInstance(APP_NAME));
            CollectionReference fotos = db.collection("Pets").document(firestoreId).collection("fotos");
            DocumentReference[] refs = photoIds.stream().map(fotos::document).toArray(DocumentReference[]::new);
            docs = db.getAll(refs).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Unavailable("Leitura do Firestore interrompida.", e);
        } catch (Exception e) {
            throw new Unavailable("Firestore indisponível para a API.", e);
        }
        Map<String, DocumentSnapshot> byId = new HashMap<>();
        for (DocumentSnapshot doc : docs) {
            byId.put(doc.getId(), doc);
        }
        List<GalleryPhoto> photos = new ArrayList<>();
        for (String id : photoIds) {
            DocumentSnapshot doc = byId.get(id);
            if (doc == null || !doc.exists() || !firestoreId.equals(text(doc, "petId"))) {
                continue;
            }
            String url = text(doc, "url");
            if (url == null || !url.startsWith(CLOUDINARY_HTTPS)) {
                continue;
            }
            photos.add(new GalleryPhoto(id, text(doc, "userId"), url));
        }
        return photos;
    }

    private static String text(DocumentSnapshot doc, String field) {
        Object value = doc.get(field);
        return value instanceof String s ? LegacyValues.trimToNull(s) : null;
    }
}
