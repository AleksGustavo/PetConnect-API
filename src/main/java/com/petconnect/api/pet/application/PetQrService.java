package com.petconnect.api.pet.application;

import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.pet.infrastructure.PetRepository;
import com.petconnect.api.shared.error.ApiException;
import com.petconnect.api.shared.web.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * QR code regenerável (RF19, issue #29). O {@code publicId} do pet é o que a
 * página pública resolve; regenerar troca esse valor e o anterior deixa de
 * funcionar na hora (não guardamos os ids antigos — um QR revogado responde
 * 404 como qualquer id desconhecido, sem confirmar que o pet existe).
 *
 * <p>Pets do app em modo Firestore são achados pelo id do documento
 * ({@code legacyFirestoreId}); os criados pela API, pelo {@code _id}. Nos
 * dois casos a posse é validada aqui e pet de outro tutor responde 404.
 *
 * <p>A troca é um único {@code findAndModify} condicionado ao
 * {@code publicId} que o app está mostrando: dois pedidos simultâneos (dois
 * toques, dois aparelhos) nunca geram dois QRs ativos nem trocam duas vezes —
 * o segundo recebe 409 e o app busca o QR atual.
 */
@Service
public class PetQrService {

    private static final Logger log = LoggerFactory.getLogger(PetQrService.class);

    /** Regenerações por pet a cada 24 h — uso legítimo é raro (suspeita de abuso, coleira perdida). */
    static final int MAX_ROTATIONS_PER_DAY = 5;
    private static final Duration WINDOW = Duration.ofHours(24);

    private final PetRepository pets;
    private final MongoTemplate mongo;
    private final RateLimiter limiter;

    public PetQrService(PetRepository pets, MongoTemplate mongo, RateLimiter limiter) {
        this.pets = pets;
        this.mongo = mongo;
        this.limiter = limiter;
    }

    public Pet current(String tutorId, String petId) {
        return owned(tutorId, pets.findById(petId).orElse(null));
    }

    public Pet currentByLegacyId(String tutorId, String firestoreId) {
        return owned(tutorId, pets.findByLegacyFirestoreId(firestoreId).orElse(null));
    }

    public Pet rotate(String tutorId, String petId, String expectedPublicId) {
        return rotate(current(tutorId, petId), expectedPublicId);
    }

    public Pet rotateByLegacyId(String tutorId, String firestoreId, String expectedPublicId) {
        return rotate(currentByLegacyId(tutorId, firestoreId), expectedPublicId);
    }

    private Pet rotate(Pet pet, String expectedPublicId) {
        // Conta também tentativas que terminam em 409: o limite é contra
        // repetição em massa, não só contra trocas bem-sucedidas.
        if (!limiter.tryConsume("qr-rotation:" + pet.getId(), MAX_ROTATIONS_PER_DAY, WINDOW)) {
            throw ApiException.tooManyRequests(
                    "Este QR code já foi regenerado muitas vezes hoje. Tente novamente amanhã.");
        }

        Instant now = Instant.now();
        Query query = new Query(Criteria.where("_id").is(pet.getId()).and("publicId").is(expectedPublicId));
        Update update = new Update()
                .set("publicId", UUID.randomUUID().toString())
                .set("publicIdRotatedAt", now)
                .set("updatedAt", now)
                .inc("publicIdRotations", 1);
        Pet updated = mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Pet.class);
        if (updated == null) {
            throw ApiException.conflict(
                    "O QR code deste pet já foi trocado. Atualize para ver o QR code atual.");
        }

        // Sem publicId (antigo ou novo) no log: quem tem o id tem o acesso à
        // página pública. O _id interno basta para auditar.
        log.info("QR code do pet {} regenerado ({}ª vez).", updated.getId(), updated.getPublicIdRotations());
        return updated;
    }

    private static Pet owned(String tutorId, Pet pet) {
        if (pet == null || !tutorId.equals(pet.getTutorId())) {
            throw ApiException.notFound("Pet não encontrado.");
        }
        return pet;
    }
}
