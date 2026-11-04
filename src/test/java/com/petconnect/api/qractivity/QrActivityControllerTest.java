package com.petconnect.api.qractivity;

import com.petconnect.api.location.domain.Location;
import com.petconnect.api.location.domain.LocationSource;
import com.petconnect.api.location.domain.PositionSource;
import com.petconnect.api.location.infrastructure.LocationRepository;
import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.pet.infrastructure.PetRepository;
import com.petconnect.api.qractivity.infrastructure.QrAccessEventRepository;
import com.petconnect.api.shared.security.FirebaseTokenVerifier;
import com.petconnect.api.shared.security.VerifiedToken;
import com.petconnect.api.shared.web.RateLimiter;
import com.petconnect.api.sharing.application.FirestorePets;
import com.petconnect.api.sharing.application.FirestorePets.FirestorePet;
import com.petconnect.api.sharing.infrastructure.PublicSharingRepository;
import com.petconnect.api.user.domain.User;
import com.petconnect.api.user.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Acessos e avistamentos do QR (issue #37 do app, fase 3 do épico #102). */
@SpringBootTest
@AutoConfigureMockMvc
class QrActivityControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    PetRepository pets;
    @Autowired
    PublicSharingRepository sharings;
    @Autowired
    LocationRepository locations;
    @Autowired
    QrAccessEventRepository accesses;
    @Autowired
    RateLimiter rateLimiter;

    @MockitoBean
    FirebaseTokenVerifier verifier;
    @MockitoBean
    FirestorePets firestore;

    /** Pet do Firestore que a migração já levou ao MongoDB. */
    private static final String REX = "fs-rex";
    /** Pet do Firestore criado depois da última migração: só existe lá. */
    private static final String NOVO = "fs-novo";
    private static final String MEMORIAL = "fs-memorial";

    private String rexApiId;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        pets.deleteAll();
        sharings.deleteAll();
        locations.deleteAll();
        accesses.deleteAll();
        rateLimiter.clear();
        String tutorA = users.save(User.provision("uid-a", "a@ex.com", "Ana", null)).getId();
        users.save(User.provision("uid-b", "b@ex.com", "Bia", null));
        when(verifier.verify(eq("tok-a"))).thenReturn(new VerifiedToken("uid-a", "a@ex.com", "Ana", null));
        when(verifier.verify(eq("tok-b"))).thenReturn(new VerifiedToken("uid-b", "b@ex.com", "Bia", null));

        when(firestore.find(anyString())).thenReturn(Optional.empty());
        when(firestore.find(REX)).thenReturn(Optional.of(
                new FirestorePet("uid-a", "Rex", "Cachorro", null, null, null, false)));
        when(firestore.find(NOVO)).thenReturn(Optional.of(
                new FirestorePet("uid-a", "Luna", "Gato", null, null, null, false)));
        when(firestore.find(MEMORIAL)).thenReturn(Optional.of(
                new FirestorePet("uid-a", "Bidu", "Cachorro", null, null, null, true)));

        rexApiId = pets.save(migrado(tutorA, REX)).getId();
        pets.save(migrado(tutorA, MEMORIAL));
    }

    private static Pet migrado(String tutorId, String firestoreId) {
        Pet p = new Pet();
        p.setTutorId(tutorId);
        p.setName(firestoreId);
        p.setLegacyFirestoreId(firestoreId);
        p.setLegacyImport(true);
        p.setPublicId(publicIdDe(firestoreId));
        return p;
    }

    private static String publicIdDe(String firestoreId) {
        return UUID.nameUUIDFromBytes(("pet:" + firestoreId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private ResultActions abrirPagina(String publicId) throws Exception {
        return mvc.perform(post("/api/v1/public/pets/" + publicId + "/events")
                .contentType("application/json").content("{\"type\":\"qr_page_opened\"}"));
    }

    private ResultActions relatar(String publicId, String json) throws Exception {
        return mvc.perform(post("/api/v1/public/pets/" + publicId + "/sightings")
                .contentType("application/json").content(json));
    }

    private ResultActions atividade(String token, String firestoreId) throws Exception {
        return mvc.perform(get("/api/v1/legacy-pets/" + firestoreId + "/qr-activity")
                .header("Authorization", "Bearer " + token));
    }

    // ------------------------------------------------------------ qr_page_opened

    @Test
    void abrirAPaginaGravaUmAcessoSemCoordenadaNemAvistamento() throws Exception {
        abrirPagina(publicIdDe(REX)).andExpect(status().isAccepted()).andExpect(content().string(""));

        assertThat(accesses.findAll()).singleElement().satisfies(e -> {
            assertThat(e.getPetKey()).isEqualTo("fs:" + REX);
            assertThat(e.getApiPetId()).isEqualTo(rexApiId);
            assertThat(e.getExpiresAt()).isAfter(Instant.now().plusSeconds(89L * 24 * 3600));
        });
        assertThat(locations.findAll()).isEmpty();
    }

    @Test
    void eventoDesconhecidoOuPetInvalidoNaoGravaNada() throws Exception {
        mvc.perform(post("/api/v1/public/pets/" + publicIdDe(REX) + "/events")
                        .contentType("application/json").content("{\"type\":\"sighting_location_shared\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/public/pets/" + publicIdDe(REX) + "/events")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        abrirPagina("nao-existe").andExpect(status().isNotFound());
        abrirPagina(publicIdDe(MEMORIAL)).andExpect(status().isNotFound());

        assertThat(accesses.findAll()).isEmpty();
    }

    @Test
    void acimaDoLimitePorHoraAPaginaSegueNormalSemGravar() throws Exception {
        for (int i = 0; i < 35; i++) {
            abrirPagina(publicIdDe(REX)).andExpect(status().isAccepted());
        }
        assertThat(accesses.count()).isEqualTo(30);
    }

    // ------------------------------------------------------------ sighting_location_shared

    @Test
    void relatoComLocalizacaoGuardaPrecisaoEOrigem() throws Exception {
        relatar(publicIdDe(REX), "{\"latitude\":-22.17,\"longitude\":-47.39,\"accuracyMeters\":35,"
                + "\"description\":\"Perto da praça\",\"clientRequestId\":\"" + UUID.randomUUID() + "\"}")
                .andExpect(status().isCreated())
                .andExpect(content().string(""));

        assertThat(locations.findAll()).singleElement().satisfies(l -> {
            assertThat(l.getAccuracyMeters()).isEqualTo(35.0);
            assertThat(l.getPositionSource()).isEqualTo(PositionSource.BROWSER_GEOLOCATION);
            assertThat(l.getLegacyPetId()).isEqualTo(REX);
            assertThat(l.getSource()).isEqualTo(LocationSource.PUBLIC_QR);
        });
    }

    @Test
    void relatoSoComTextoFicaSemCoordenada() throws Exception {
        relatar(publicIdDe(REX), "{\"description\":\"Vi na rua 7\",\"accuracyMeters\":10}")
                .andExpect(status().isCreated());

        assertThat(locations.findAll()).singleElement().satisfies(l -> {
            assertThat(l.getLatitude()).isNull();
            assertThat(l.getAccuracyMeters()).isNull();
            assertThat(l.getPositionSource()).isEqualTo(PositionSource.NONE);
        });
    }

    @Test
    void relatoInvalidoResponde400() throws Exception {
        relatar(publicIdDe(REX), "{\"latitude\":-22.17}").andExpect(status().isBadRequest());
        relatar(publicIdDe(REX), "{\"description\":\"   \"}").andExpect(status().isBadRequest());
        relatar(publicIdDe(REX), "{\"latitude\":91,\"longitude\":0}").andExpect(status().isBadRequest());
        relatar(publicIdDe(REX), "{\"latitude\":0,\"longitude\":0,\"accuracyMeters\":-1}")
                .andExpect(status().isBadRequest());
        relatar(publicIdDe(REX), "{\"description\":\"x\",\"clientRequestId\":\"<script>\"}")
                .andExpect(status().isBadRequest());

        assertThat(locations.findAll()).isEmpty();
    }

    @Test
    void repetirOMesmoEnvioNaoDuplicaORelato() throws Exception {
        String body = "{\"description\":\"Perto da escola\",\"clientRequestId\":\"" + UUID.randomUUID() + "\"}";
        relatar(publicIdDe(REX), body).andExpect(status().isCreated());
        relatar(publicIdDe(REX), body).andExpect(status().isCreated());

        assertThat(locations.count()).isEqualTo(1);
    }

    @Test
    void muitosRelatosParaOMesmoPetRespondem429() throws Exception {
        for (int i = 0; i < 20; i++) {
            relatar(publicIdDe(REX), "{\"description\":\"relato " + i + "\"}").andExpect(status().isCreated());
        }
        relatar(publicIdDe(REX), "{\"description\":\"mais um\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    // ------------------------------------------------------------ tutor

    @Test
    void tutorVeAvistamentosEAcessosSeparados() throws Exception {
        abrirPagina(publicIdDe(REX));
        abrirPagina(publicIdDe(REX));
        relatar(publicIdDe(REX), "{\"latitude\":-22.17,\"longitude\":-47.39,\"accuracyMeters\":35}");
        Location doTutor = new Location();
        doTutor.setPetId(rexApiId);
        doTutor.setSource(LocationSource.TUTOR);
        doTutor.setDescription("registro do tutor");
        locations.save(doTutor);

        atividade("tok-a", REX)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sightings", hasSize(1)))
                .andExpect(jsonPath("$.sightings[0].latitude").value(-22.17))
                .andExpect(jsonPath("$.sightings[0].accuracyMeters").value(35.0))
                .andExpect(jsonPath("$.sightings[0].positionSource").value("BROWSER_GEOLOCATION"))
                .andExpect(jsonPath("$.recentAccesses", hasSize(2)))
                .andExpect(jsonPath("$.accessesLast30Days").value(2));
    }

    @Test
    void outroTutorOuSemTokenNaoVe() throws Exception {
        relatar(publicIdDe(REX), "{\"description\":\"Perto da praça\"}");

        atividade("tok-b", REX).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/legacy-pets/" + REX + "/qr-activity")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/pets/" + rexApiId + "/qr-activity")
                        .header("Authorization", "Bearer tok-b"))
                .andExpect(status().isNotFound());
    }

    @Test
    void petAindaNaoMigradoTemAvistamentoVisivelParaOTutor() throws Exception {
        mvc.perform(put("/api/v1/legacy-pets/" + NOVO + "/public-sharing")
                        .header("Authorization", "Bearer tok-a")
                        .contentType("application/json")
                        .content("{\"enabled\":true,\"consentAccepted\":true,\"consentVersion\":\"2026-11-02\","
                                + "\"fields\":[\"PET_NAME\"]}"))
                .andExpect(status().isOk());

        abrirPagina(publicIdDe(NOVO)).andExpect(status().isAccepted());
        relatar(publicIdDe(NOVO), "{\"latitude\":-23.5,\"longitude\":-46.6,\"accuracyMeters\":12}")
                .andExpect(status().isCreated());

        atividade("tok-a", NOVO)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sightings", hasSize(1)))
                .andExpect(jsonPath("$.accessesLast30Days").value(1));
    }

    @Test
    void historicoAntigoDoTutorContinuaForaENovoRelatoAparece() throws Exception {
        Location antigo = new Location();
        antigo.setLegacyPetId(REX);
        antigo.setSource(LocationSource.PUBLIC_QR);
        antigo.setReportedAt(Instant.parse("2025-01-10T12:00:00Z"));
        antigo.setDescription("relato migrado");
        antigo.setLegacyImport(true);
        locations.save(antigo);
        relatar(publicIdDe(REX), "{\"description\":\"novo\"}");

        atividade("tok-a", REX)
                .andExpect(jsonPath("$.sightings", hasSize(2)))
                .andExpect(jsonPath("$.sightings[0].description").value("novo"))
                .andExpect(jsonPath("$.sightings[0].positionSource").value("NONE"))
                .andExpect(jsonPath("$.sightings[1].positionSource").value(nullValue()));
    }

    @Test
    void excluirOPetApagaOsAcessos() throws Exception {
        abrirPagina(publicIdDe(REX));
        assertThat(accesses.count()).isEqualTo(1);

        mvc.perform(delete("/api/v1/pets/" + rexApiId).header("Authorization", "Bearer tok-a"))
                .andExpect(status().isNoContent());

        assertThat(accesses.findAll()).isEmpty();
        assertThat(List.copyOf(pets.findAll())).noneMatch(p -> p.getId().equals(rexApiId));
    }
}
