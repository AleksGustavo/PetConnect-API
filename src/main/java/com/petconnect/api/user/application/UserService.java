package com.petconnect.api.user.application;

import com.petconnect.api.shared.error.ApiException;
import com.petconnect.api.shared.security.AuthenticatedUser;
import com.petconnect.api.shared.security.AuthenticatedUserResolver;
import com.petconnect.api.shared.security.VerifiedToken;
import com.petconnect.api.user.domain.User;
import com.petconnect.api.user.infrastructure.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class UserService implements AuthenticatedUserResolver {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    /**
     * Garante o documento em {@code users} para o token verificado
     * (provisiona no primeiro acesso) e devolve o principal.
     */
    @Override
    public AuthenticatedUser resolve(VerifiedToken token) {
        User user = users.findByFirebaseUid(token.uid())
                .orElseGet(() -> createFrom(token));
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
}
