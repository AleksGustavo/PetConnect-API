package com.petconnect.api.user.application;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.petconnect.api.location.infrastructure.LocationRepository;
import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.pet.infrastructure.PetRepository;
import com.petconnect.api.shared.error.ApiException;
import com.petconnect.api.shared.security.AuthenticatedUser;
import com.petconnect.api.shared.security.AuthenticatedUserResolver;
import com.petconnect.api.shared.security.TokenVerificationException;
import com.petconnect.api.shared.security.VerifiedToken;
import com.petconnect.api.user.domain.User;
import com.petconnect.api.user.infrastructure.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class UserService implements AuthenticatedUserResolver {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository users;
    private final PetRepository pets;
    private final LocationRepository locations;
    private final ObjectProvider<FirebaseAuth> firebaseAuth;

    public UserService(UserRepository users, PetRepository pets, LocationRepository locations,
                       ObjectProvider<FirebaseAuth> firebaseAuth) {
        this.users = users;
        this.pets = pets;
        this.locations = locations;
        this.firebaseAuth = firebaseAuth;
    }

    /**
     * Garante o documento em {@code users} para o token verificado
     * (provisiona no primeiro acesso) e devolve o principal.
     */
    @Override
    public AuthenticatedUser resolve(VerifiedToken token) {
        User user = users.findByFirebaseUid(token.uid())
                .orElseGet(() -> createFrom(token));
        if (user.getDeletedAt() != null) {
            // Conta excluída (RF09). O token do Firebase pode continuar válido
            // por até ~1h; recusamos aqui de qualquer forma.
            throw new TokenVerificationException("Conta excluída.");
        }
        return new AuthenticatedUser(user.getId(), user.getFirebaseUid(), user.getEmail(), user.getRoles());
    }

    private User createFrom(VerifiedToken token) {
        User novo = User.provision(token.uid(), token.email(), token.name(), token.picture());
        try {
            return users.save(novo);
        } catch (DuplicateKeyException race) {
            // criado concorrentemente entre o findBy e o save
            return users.findByFirebaseUid(token.uid()).orElseThrow();
        }
    }

    public User getByFirebaseUid(String firebaseUid) {
        return users.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));
    }

    public User update(String firebaseUid, ProfileUpdate patch) {
        User user = getByFirebaseUid(firebaseUid);
        if (patch.firstName() != null) {
            user.setFirstName(patch.firstName().isBlank() ? null : patch.firstName().trim());
        }
        if (patch.lastName() != null) {
            user.setLastName(patch.lastName().isBlank() ? null : patch.lastName().trim());
        }
        if (patch.phone() != null) {
            user.setPhone(patch.phone().isBlank() ? null : patch.phone().trim());
        }
        if (patch.birthDate() != null) {
            user.setBirthDate(patch.birthDate());
        }
        if (patch.gender() != null) {
            user.setGender(patch.gender());
        }
        if (patch.photoUrl() != null) {
            user.setPhotoUrl(patch.photoUrl().isBlank() ? null : patch.photoUrl().trim());
        }
        return users.save(user);
    }

    /**
     * Exclusão de conta (RF09): apaga em cascata os pets do tutor e seus
     * registros de localização, faz soft-delete do documento do usuário e
     * remove o usuário do Firebase Auth (quando o Admin SDK está configurado).
     */
    public void deleteAccount(String firebaseUid) {
        User user = getByFirebaseUid(firebaseUid);

        List<Pet> ownedPets = pets.findByTutorId(user.getId());
        List<String> petIds = ownedPets.stream().map(Pet::getId).toList();
        if (!petIds.isEmpty()) {
            locations.deleteByPetIdIn(petIds);
            pets.deleteAll(ownedPets);
        }

        user.setDeletedAt(Instant.now());
        users.save(user);

        FirebaseAuth auth = firebaseAuth.getIfAvailable();
        if (auth != null) {
            try {
                auth.deleteUser(firebaseUid);
            } catch (FirebaseAuthException e) {
                // Não falha a operação: o cliente ainda pode chamar user.delete()
                // no próprio SDK. O documento já foi marcado como excluído.
                log.warn("Falha ao remover {} do Firebase Auth: {}", firebaseUid, e.getMessage());
            }
        } else {
            log.info("Firebase Admin SDK ausente — usuário {} não removido do Auth pelo servidor.", firebaseUid);
        }

        log.info("Conta excluída: user {} ({} pets, cascata de localizações).", user.getId(), petIds.size());
    }
}
