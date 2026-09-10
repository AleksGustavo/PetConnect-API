package com.petconnect.api.user.web;

import com.petconnect.api.user.domain.User;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

/** Representação pública de um tutor. Nunca expõe o documento Mongo cru. */
public record UserResponse(
        String id,
        String firebaseUid,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String phone,
        LocalDate birthDate,
        String gender,
        String photoUrl,
        Set<String> roles,
        Instant createdAt,
        Instant updatedAt
) {

    public static UserResponse from(User u) {
        String full = u.getLastName() == null || u.getLastName().isBlank()
                ? nullSafe(u.getFirstName())
                : (nullSafe(u.getFirstName()) + " " + u.getLastName()).trim();
        return new UserResponse(
                u.getId(),
                u.getFirebaseUid(),
                u.getEmail(),
                u.getFirstName(),
                u.getLastName(),
                full.isBlank() ? null : full,
                u.getPhone(),
                u.getBirthDate(),
                u.getGender() == null ? null : u.getGender().name(),
                u.getPhotoUrl(),
                u.getRoles(),
                u.getCreatedAt(),
                u.getUpdatedAt());
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
