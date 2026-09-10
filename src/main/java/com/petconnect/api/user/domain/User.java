package com.petconnect.api.user.domain;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tutor. Coleção {@code users}. 1 documento por conta do Firebase Auth.
 * Ver {@code docs/database/mongodb-target-schema.md} (no repo do app).
 */
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String firebaseUid;

    @Indexed(unique = true, sparse = true)
    private String email;

    private String firstName;
    private String lastName;
    private String phone;
    private LocalDate birthDate;
    private Gender gender;
    private String photoUrl;

    /** ← {@code usuarioID}/{@code uid} do Firestore, preservado; sem uso funcional. */
    private String legacyUsuarioId;

    @Field("roles")
    private Set<String> roles = new LinkedHashSet<>(Set.of(Role.TUTOR.name()));

    /** Origem do documento: {@code true} se veio da migração do Firestore. */
    private boolean legacyImport;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private Instant deletedAt;

    protected User() {
    }

    public static User provision(String firebaseUid, String email, String firstName, String photoUrl) {
        User u = new User();
        u.firebaseUid = firebaseUid;
        u.email = email;
        u.firstName = (firstName == null || firstName.isBlank()) ? null : firstName.trim();
        u.photoUrl = photoUrl;
        u.roles = new LinkedHashSet<>(Set.of(Role.TUTOR.name()));
        return u;
    }

    public String getId() {
        return id;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getLegacyUsuarioId() {
        return legacyUsuarioId;
    }

    public void setLegacyUsuarioId(String legacyUsuarioId) {
        this.legacyUsuarioId = legacyUsuarioId;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public boolean isLegacyImport() {
        return legacyImport;
    }

    public void setLegacyImport(boolean legacyImport) {
        this.legacyImport = legacyImport;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
