package com.petconnect.api.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petconnect.api.location.domain.Location;
import com.petconnect.api.location.domain.LocationSource;
import com.petconnect.api.location.infrastructure.LocationRepository;
import com.petconnect.api.migration.support.LegacyValues;
import com.petconnect.api.migration.support.PhotoResolver;
import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.pet.domain.PetGender;
import com.petconnect.api.pet.domain.PetSize;
import com.petconnect.api.pet.domain.Species;
import com.petconnect.api.pet.infrastructure.PetRepository;
import com.petconnect.api.user.domain.Gender;
import com.petconnect.api.user.domain.Role;
import com.petconnect.api.user.domain.User;
import com.petconnect.api.user.infrastructure.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Motor da migração Firestore → MongoDB (FASE 3). Recebe o JSON do export já
 * parseado e grava {@code users}, {@code pets}, {@code locations} +
 * {@code migration_audit}. Idempotente: cada execução apaga os documentos
 * {@code legacyImport=true} e a trilha de auditoria antes de reprocessar.
 *
 * <p>Regras de conversão: {@code docs/database/firestore-data-report.md}.
 */
@Service
public class LegacyMigrationService {

    private static final Logger log = LoggerFactory.getLogger(LegacyMigrationService.class);

    /** Contas de teste — não migradas (lista fechada do relatório de dados). */
    static final Set<String> EXCLUDED_USER_IDS = Set.of(
            "8cwyWeYyklSDkDW8dGt7Y59sYlU2",
            "LVyo69hQEgWYuVX6cPuvOTqMcK82",
            "T1yKCSbsj4btGLdvbZ1bQjQAc5H2",
            "TtHZWAgUOoMn4x2H3BrI5ldRol93",
            "VK4h7q3AwdWkzVHGFMVTILiAfHq1",
            "jKClEhIJd3SM0Pf3G8imIVhYaNE2",
            "xVQpsGte79VmgAAUdHJ6JqLSD3T2");

    private final UserRepository users;
    private final PetRepository pets;
    private final LocationRepository locations;
    private final MigrationAuditRepository audits;
    private final MongoTemplate mongo;
    private final ObjectMapper objectMapper;

    public LegacyMigrationService(UserRepository users, PetRepository pets, LocationRepository locations,
                                  MigrationAuditRepository audits, MongoTemplate mongo, ObjectMapper objectMapper) {
        this.users = users;
        this.pets = pets;
        this.locations = locations;
        this.audits = audits;
        this.mongo = mongo;
        this.objectMapper = objectMapper;
    }

    public MigrationReport migrate(JsonNode root, String sourceLabel) {
        Instant startedAt = Instant.now();
        wipePreviousImport();

        Map<String, Long> warningCounts = new TreeMap<>();
        List<MigrationAudit> auditBatch = new ArrayList<>();

        JsonNode usuarios = root.path("Usuarios");
        JsonNode petsNode = root.path("Pets");
        JsonNode locsNode = root.path("Localizacoes");

        Set<String> knownUserIds = fieldNames(usuarios);

        // ---------- users ----------
        long uTotal = 0, uMig = 0, uSkip = 0, uWarn = 0;
        List<String> excludedUsers = new ArrayList<>();
        Map<String, String> userIdByFirestoreUid = new HashMap<>();

        var uit = usuarios.fields();
        while (uit.hasNext()) {
            var e = uit.next();
            uTotal++;
            String uid = e.getKey();
            JsonNode node = e.getValue();
            Map<String, Object> raw = asMap(node);

            if (EXCLUDED_USER_IDS.contains(uid)) {
                excludedUsers.add(uid);
                uSkip++;
                auditBatch.add(MigrationAudit.skipped("Usuarios", uid, "test-account", raw));
                continue;
            }

            List<String> warnings = new ArrayList<>();
            User u = mapUser(uid, node, warnings);
            User saved = users.save(u);
            userIdByFirestoreUid.put(uid, saved.getId());
            uMig++;
            if (!warnings.isEmpty()) {
                uWarn++;
                warnings.forEach(w -> bump(warningCounts, w));
            }
            auditBatch.add(MigrationAudit.migrated("Usuarios", uid, "users", saved.getId(), warnings, raw));
        }

        // ---------- pets ----------
        long pTotal = 0, pMig = 0, pSkip = 0, pWarn = 0;
        List<String> excludedPets = new ArrayList<>();
        Map<String, String> petIdByFirestoreId = new HashMap<>();

        var pit = petsNode.fields();
        while (pit.hasNext()) {
            var e = pit.next();
            pTotal++;
            String petFsId = e.getKey();
            JsonNode node = e.getValue();
            Map<String, Object> raw = asMap(node);

            String ownerId = firstText(node, "userId", "userID");
            String skipReason = null;
            if (ownerId == null) {
                skipReason = "no-owner";
            } else if (EXCLUDED_USER_IDS.contains(ownerId)) {
                skipReason = "owner-is-test-account";
            } else if (!knownUserIds.contains(ownerId)) {
                skipReason = "orphan-owner";
            } else if (!userIdByFirestoreUid.containsKey(ownerId)) {
                skipReason = "owner-not-migrated";
            }

            if (skipReason != null) {
                excludedPets.add(petFsId);
                pSkip++;
                auditBatch.add(MigrationAudit.skipped("Pets", petFsId, skipReason, raw));
                continue;
            }

            List<String> warnings = new ArrayList<>();
            Pet pet = mapPet(petFsId, node, userIdByFirestoreUid.get(ownerId), warnings);
            Pet saved = pets.save(pet);
            petIdByFirestoreId.put(petFsId, saved.getId());
            pMig++;
            if (!warnings.isEmpty()) {
                pWarn++;
                warnings.forEach(w -> bump(warningCounts, w));
            }
            auditBatch.add(MigrationAudit.migrated("Pets", petFsId, "pets", saved.getId(), warnings, raw));
        }

        // ---------- locations ----------
        long lTotal = 0, lMig = 0, lSkip = 0, lWarn = 0;

        var lit = locsNode.fields();
        while (lit.hasNext()) {
            var e = lit.next();
            lTotal++;
            String locId = e.getKey();
            JsonNode node = e.getValue();
            Map<String, Object> raw = asMap(node);

            String legacyPetId = text(node, "petId");
            String targetPetId = legacyPetId == null ? null : petIdByFirestoreId.get(legacyPetId);

            if (targetPetId == null) {
                lSkip++;
                auditBatch.add(MigrationAudit.skipped("Localizacoes", locId, "pet-not-migrated", raw));
                continue;
            }

            Double lat = doubleOrNull(node, "latitude");
            Double lng = doubleOrNull(node, "longitude");
            if (outsideBrazil(lat, lng)) {
                lSkip++;
                auditBatch.add(MigrationAudit.skipped("Localizacoes", locId, "foreign-coordinate", raw));
                continue;
            }

            List<String> warnings = new ArrayList<>();
            Location loc = mapLocation(locId, node, targetPetId, legacyPetId, lat, lng, warnings);
            Location saved = locations.save(loc);
            lMig++;
            if (!warnings.isEmpty()) {
                lWarn++;
                warnings.forEach(w -> bump(warningCounts, w));
            }
            auditBatch.add(MigrationAudit.migrated("Localizacoes", locId, "locations", saved.getId(), warnings, raw));
        }

        audits.saveAll(auditBatch);

        MigrationReport report = new MigrationReport(
                startedAt, Instant.now(), sourceLabel,
                new MigrationReport.Section(uTotal, uMig, uSkip, 0, uWarn),
                new MigrationReport.Section(pTotal, pMig, pSkip, 0, pWarn),
                new MigrationReport.Section(lTotal, lMig, lSkip, 0, lWarn),
                excludedUsers, excludedPets, lSkip, warningCounts);

        log.info("Migração concluída: users {}/{} · pets {}/{} · locations {}/{} · avisos {}",
                uMig, uTotal, pMig, pTotal, lMig, lTotal, warningCounts);
        return report;
    }

    // ------------------------------------------------------------------ mappers

    private User mapUser(String firebaseUid, JsonNode n, List<String> warnings) {
        String nome = text(n, "nome");
        if (nome != null && nome.contains("@")) {
            warnings.add("name-looks-like-email");
        }
        User u = User.provision(firebaseUid, text(n, "email"), nome, null);
        u.setLastName(LegacyValues.trimToNull(text(n, "sobrenome")));
        u.setPhone(LegacyValues.cleanPhone(text(n, "telefone")));

        String rawDob = firstText(n, "dataNascimento", "datadenascimento");
        LocalDate dob = LegacyValues.parseDate(rawDob);
        if (dob == null && LegacyValues.hasText(rawDob)) {
            warnings.add("invalid-birthdate-dropped");
        }
        u.setBirthDate(dob);

        Gender g = LegacyValues.mapUserGender(text(n, "genero"));
        u.setGender(g);

        PhotoResolver.Result photo = PhotoResolver.resolve(
                text(n, "foto"), text(n, "imagemUrl"), text(n, "photoURL"), text(n, "imagemUrlUsuario"));
        u.setPhotoUrl(photo.url());
        if (photo.warning() != null) {
            warnings.add(photo.warning());
        }

        u.setLegacyUsuarioId(firstText(n, "usuarioID", "uid"));
        u.setLegacyImport(true);
        u.setMigrationWarnings(new ArrayList<>(warnings));
        u.setRoles(new java.util.LinkedHashSet<>(Set.of(Role.TUTOR.name())));
        return u;
    }

    private Pet mapPet(String legacyId, JsonNode n, String tutorId, List<String> warnings) {
        Pet p = new Pet();
        p.setTutorId(tutorId);
        p.setLegacyFirestoreId(legacyId);
        p.setLegacyImport(true);
        // publicId determinístico: reexecutar a migração não troca o QR do pet.
        p.setPublicId(java.util.UUID.nameUUIDFromBytes(("pet:" + legacyId).getBytes()).toString());
        p.setName(LegacyValues.trimToNull(text(n, "nome")));
        p.setBreed(LegacyValues.trimToNull(text(n, "raca")));
        p.setColor(LegacyValues.trimToNull(text(n, "cor")));

        String rawEspecie = text(n, "especie");
        Species sp = LegacyValues.mapSpecies(rawEspecie);
        if (sp == Species.OTHER && LegacyValues.hasText(rawEspecie)
                && !rawEspecie.trim().equalsIgnoreCase("outro")) {
            warnings.add("species-unmapped");
        }
        p.setSpecies(sp);

        p.setGender(LegacyValues.mapPetGender(text(n, "genero")));

        String rawPorte = text(n, "porte");
        PetSize size = LegacyValues.mapSize(rawPorte);
        if (size == null && LegacyValues.hasText(rawPorte)) {
            warnings.add("size-unmapped");
        }
        p.setSize(size);

        Object rawPeso = raw(n, "peso");
        Double kg = LegacyValues.parseWeightKg(rawPeso);
        if (kg == null && rawPeso != null && !String.valueOf(rawPeso).isBlank()) {
            warnings.add("weight-unparseable");
        }
        p.setWeightKg(kg);

        String rawDob = firstText(n, "dataNascimento", "datadenascimento");
        LocalDate dob = LegacyValues.parseDate(rawDob);
        if (dob == null && LegacyValues.hasText(rawDob)) {
            warnings.add("invalid-birthdate-dropped");
        }
        p.setBirthDate(dob);

        if (n.hasNonNull("vacinado")) {
            p.setVaccinatedFlag(n.get("vacinado").asBoolean());
        }
        p.setPublicContactPhone(LegacyValues.cleanPhone(text(n, "telefone")));

        PhotoResolver.Result photo = PhotoResolver.resolve(
                text(n, "foto"), text(n, "imagemUrl"), text(n, "imageUrl"), text(n, "imagemUrlPet"));
        p.setPhotoUrl(photo.url());
        if (photo.warning() != null) {
            warnings.add(photo.warning());
        }

        p.setLegacyQrCodeId(LegacyValues.trimToNull(text(n, "qrCodeId")));
        p.setMigrationWarnings(new ArrayList<>(warnings));
        return p;
    }

    private Location mapLocation(String legacyId, JsonNode n, String petId, String legacyPetId,
                                 Double lat, Double lng, List<String> warnings) {
        Location loc = new Location();
        loc.setPetId(petId);
        loc.setLegacyPetId(legacyPetId);
        loc.setLegacyPetName(LegacyValues.trimToNull(text(n, "nomePet")));
        loc.setLegacyTutorName(LegacyValues.trimToNull(text(n, "nomeTutor")));
        loc.setLatitude(lat);
        loc.setLongitude(lng);
        loc.setReporterContact(LegacyValues.cleanPhone(text(n, "telefone")));
        loc.setSource(LocationSource.PUBLIC_QR);
        loc.setLegacyImport(true);

        String ts = text(n, "timestamp");
        if (ts != null) {
            try {
                loc.setReportedAt(Instant.parse(ts));
            } catch (Exception ex) {
                warnings.add("unparseable-timestamp");
            }
        }
        if (lat == null || lng == null) {
            warnings.add("missing-coordinates");
        }
        loc.setMigrationWarnings(new ArrayList<>(warnings));
        return loc;
    }

    // ------------------------------------------------------------------ helpers

    private void wipePreviousImport() {
        Query legacy = new Query(Criteria.where("legacyImport").is(true));
        long u = mongo.remove(legacy, User.class).getDeletedCount();
        long p = mongo.remove(legacy, Pet.class).getDeletedCount();
        long l = mongo.remove(legacy, Location.class).getDeletedCount();
        audits.deleteAll();
        if (u + p + l > 0) {
            log.info("Reexecução: removidos {} users, {} pets, {} locations do import anterior.", u, p, l);
        }
    }

    private static boolean outsideBrazil(Double lat, Double lng) {
        if (lat == null || lng == null) {
            return false; // sem coordenada não é "estrangeira", é incompleta
        }
        return lat < -34.0 || lat > 6.0 || lng < -74.0 || lng > -33.0;
    }

    private static void bump(Map<String, Long> counts, String key) {
        counts.merge(key, 1L, Long::sum);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return s == null || s.isEmpty() ? null : s;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String f : fields) {
            String v = text(node, f);
            if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static Object raw(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isNumber()) {
            return v.numberValue();
        }
        return v.asText();
    }

    private static Double doubleOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull() || !v.isNumber()) ? null : v.asDouble();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(JsonNode node) {
        return objectMapper.convertValue(node, LinkedHashMap.class);
    }
}
