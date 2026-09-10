package com.petconnect.api.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petconnect.api.location.domain.Location;
import com.petconnect.api.location.infrastructure.LocationRepository;
import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.pet.domain.PetSize;
import com.petconnect.api.pet.domain.Species;
import com.petconnect.api.pet.infrastructure.PetRepository;
import com.petconnect.api.user.domain.Gender;
import com.petconnect.api.user.domain.User;
import com.petconnect.api.user.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LegacyMigrationServiceTest {

    @Autowired
    LegacyMigrationService service;
    @Autowired
    UserRepository users;
    @Autowired
    PetRepository pets;
    @Autowired
    LocationRepository locations;
    @Autowired
    MigrationAuditRepository audits;
    @Autowired
    ObjectMapper mapper;

    /** Export sintético cobrindo cada regra de conversão. */
    private static final String EXPORT = """
            {
              "Usuarios": {
                "uid-ok": {
                  "nome": "João", "sobrenome": "Silva", "email": "joao@ex.com",
                  "telefone": "19 99999-0000", "genero": "Homem",
                  "dataNascimento": "28/07/1997",
                  "foto": "https://res.cloudinary.com/x/image/upload/v1/abc.jpg",
                  "usuarioID": "uuid-legacy-1"
                },
                "uid-bad": {
                  "nome": "Teste Datas", "email": "d@ex.com", "genero": "Mulher",
                  "dataNascimento": "13/32/321",
                  "foto": "https://firebasestorage.googleapis.com/v0/b/x/o/y.jpg"
                },
                "uid-email-name": {
                  "nome": "fulano@gmail.com", "email": "fulano@gmail.com", "genero": "Outro"
                },
                "T1yKCSbsj4btGLdvbZ1bQjQAc5H2": {
                  "nome": "pizza", "email": "testepodeapagar@g.com"
                }
              },
              "Pets": {
                "pet-ok": {
                  "nome": "Rex", "especie": "Cachorro", "raca": "SRD", "cor": "Preto",
                  "genero": "Macho", "porte": "Médio", "peso": "10 KG",
                  "dataNascimento": "2019-05-01", "userId": "uid-ok", "vacinado": true,
                  "foto": "https://res.cloudinary.com/x/image/upload/v1/rex.jpg"
                },
                "pet-weight-junk": {
                  "nome": "Mingau", "especie": "gato", "peso": "byi",
                  "porte": "ta", "userId": "uid-ok"
                },
                "pet-species-junk": {
                  "nome": "Coisa", "especie": "guff", "peso": 7.4, "userId": "uid-bad"
                },
                "pet-excluded-owner": {
                  "nome": "Gay", "especie": "gato", "userId": "T1yKCSbsj4btGLdvbZ1bQjQAc5H2"
                },
                "pet-orphan": {
                  "nome": "Branca", "especie": "cao", "userId": "NAO-EXISTE-NO-USUARIOS"
                }
              },
              "Localizacoes": {
                "loc-ok": {
                  "latitude": -22.17, "longitude": -47.39, "telefone": "19 98888-1111",
                  "petId": "pet-ok", "nomePet": "Rex", "nomeTutor": "João",
                  "timestamp": "2024-12-04T23:04:12.964Z"
                },
                "loc-orphan-pet": {
                  "latitude": -22.1, "longitude": -47.3, "petId": "9CUlOu8NaoMigrado",
                  "nomePet": "Fantasma", "nomeTutor": "Ninguém",
                  "timestamp": "2025-01-01T00:00:00.000Z"
                },
                "loc-foreign": {
                  "latitude": 53.27, "longitude": -9.05, "petId": "pet-ok",
                  "nomePet": "Rex", "nomeTutor": "João",
                  "timestamp": "2024-12-10T18:54:45.859Z"
                }
              }
            }
            """;

    private JsonNode root() throws Exception {
        return mapper.readTree(EXPORT);
    }

    @BeforeEach
    void clean() {
        users.deleteAll();
        pets.deleteAll();
        locations.deleteAll();
        audits.deleteAll();
    }

    @Test
    void migraUsuariosValidosEExcluiContasDeTeste() throws Exception {
        MigrationReport r = service.migrate(root(), "synthetic");

        assertThat(r.users().total()).isEqualTo(4);
        assertThat(r.users().migrated()).isEqualTo(3);
        assertThat(r.users().skipped()).isEqualTo(1);
        assertThat(r.excludedUserIds()).containsExactly("T1yKCSbsj4btGLdvbZ1bQjQAc5H2");
        assertThat(users.findByFirebaseUid("T1yKCSbsj4btGLdvbZ1bQjQAc5H2")).isEmpty();

        User ok = users.findByFirebaseUid("uid-ok").orElseThrow();
        assertThat(ok.getFirstName()).isEqualTo("João");
        assertThat(ok.getLastName()).isEqualTo("Silva");
        assertThat(ok.getGender()).isEqualTo(Gender.MALE);
        assertThat(ok.getBirthDate()).hasToString("1997-07-28");
        assertThat(ok.getPhotoUrl()).contains("res.cloudinary.com");
        assertThat(ok.getLegacyUsuarioId()).isEqualTo("uuid-legacy-1");
        assertThat(ok.getRoles()).containsExactly("TUTOR");
        assertThat(ok.isLegacyImport()).isTrue();
    }

    @Test
    void trataDataInvalidaEImagemDoStorage() throws Exception {
        service.migrate(root(), "synthetic");

        User bad = users.findByFirebaseUid("uid-bad").orElseThrow();
        assertThat(bad.getBirthDate()).isNull();
        assertThat(bad.getPhotoUrl()).isNull();
        assertThat(bad.getMigrationWarnings())
                .contains("invalid-birthdate-dropped", "storage-image-lost");

        User emailName = users.findByFirebaseUid("uid-email-name").orElseThrow();
        assertThat(emailName.getMigrationWarnings()).contains("name-looks-like-email");
    }

    @Test
    void convertePesoEEspecieEFiltraPetsDeContasInvalidas() throws Exception {
        MigrationReport r = service.migrate(root(), "synthetic");

        assertThat(r.pets().total()).isEqualTo(5);
        assertThat(r.pets().migrated()).isEqualTo(3);
        assertThat(r.pets().skipped()).isEqualTo(2);
        assertThat(r.excludedPetIds()).contains("pet-excluded-owner", "pet-orphan");

        Pet rex = pets.findByLegacyFirestoreId("pet-ok").orElseThrow();
        assertThat(rex.getSpecies()).isEqualTo(Species.DOG);
        assertThat(rex.getWeightKg()).isEqualTo(10.0);
        assertThat(rex.getSize()).isEqualTo(PetSize.MEDIUM);
        assertThat(rex.getVaccinatedFlag()).isTrue();
        assertThat(rex.getBirthDate()).hasToString("2019-05-01");
        assertThat(rex.getPublicId()).isNotBlank();
        assertThat(rex.getTutorId()).isEqualTo(users.findByFirebaseUid("uid-ok").orElseThrow().getId());

        Pet junk = pets.findByLegacyFirestoreId("pet-weight-junk").orElseThrow();
        assertThat(junk.getWeightKg()).isNull();
        assertThat(junk.getMigrationWarnings()).contains("weight-unparseable", "size-unmapped");

        Pet sp = pets.findByLegacyFirestoreId("pet-species-junk").orElseThrow();
        assertThat(sp.getSpecies()).isEqualTo(Species.OTHER);
        assertThat(sp.getWeightKg()).isEqualTo(7.4);
        assertThat(sp.getMigrationWarnings()).contains("species-unmapped");
    }

    @Test
    void migraLocationsLigadasEDescartaOrfaEEstrangeira() throws Exception {
        MigrationReport r = service.migrate(root(), "synthetic");

        assertThat(r.locations().total()).isEqualTo(3);
        assertThat(r.locations().migrated()).isEqualTo(1);
        assertThat(r.locations().skipped()).isEqualTo(2);

        Location loc = locations.findAll().get(0);
        assertThat(loc.getPetId()).isEqualTo(pets.findByLegacyFirestoreId("pet-ok").orElseThrow().getId());
        assertThat(loc.getLegacyPetId()).isEqualTo("pet-ok");
        assertThat(loc.getLatitude()).isEqualTo(-22.17);
        assertThat(loc.getReporterContact()).isEqualTo("19 98888-1111");
        assertThat(loc.getReportedAt()).isNotNull();
        assertThat(loc.getSource().name()).isEqualTo("PUBLIC_QR");
    }

    @Test
    void ehIdempotente() throws Exception {
        service.migrate(root(), "run-1");
        long usersAfter1 = users.count();
        long petsAfter1 = pets.count();

        MigrationReport r2 = service.migrate(root(), "run-2");

        assertThat(users.count()).isEqualTo(usersAfter1);
        assertThat(pets.count()).isEqualTo(petsAfter1);
        assertThat(r2.users().migrated()).isEqualTo(3);
        assertThat(r2.pets().migrated()).isEqualTo(3);
        assertThat(audits.count()).isGreaterThan(0);
    }

    @Test
    void gravaTrilhaDeAuditoria() throws Exception {
        service.migrate(root(), "synthetic");

        long migrated = audits.countByOutcome(MigrationAudit.Outcome.MIGRATED);
        long skipped = audits.countByOutcome(MigrationAudit.Outcome.SKIPPED);
        assertThat(migrated).isEqualTo(3 + 3 + 1); // users + pets + locations
        assertThat(skipped).isEqualTo(1 + 2 + 2);
    }
}
