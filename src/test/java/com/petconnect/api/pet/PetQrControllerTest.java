package com.petconnect.api.pet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.pet.infrastructure.PetRepository;
import com.petconnect.api.shared.security.FirebaseTokenVerifier;
import com.petconnect.api.shared.security.VerifiedToken;
import com.petconnect.api.shared.web.RateLimiter;
import com.petconnect.api.user.domain.User;
import com.petconnect.api.user.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** QR code regenerável (RF19, issue #29). */
@SpringBootTest
@AutoConfigureMockMvc
class PetQrControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    PetRepository pets;
    @Autowired
    RateLimiter rateLimiter;
    @Autowired
    ObjectMapper mapper;

    @MockitoBean
    FirebaseTokenVerifier verifier;

    private static final String FIRESTORE_ID = "fs-rex";
    private String petId;
    private String publicIdOriginal;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        pets.deleteAll();
        rateLimiter.clear();
        String tutorA = users.save(User.provision("uid-a", "a@ex.com", "Ana", null)).getId();
        users.save(User.provision("uid-b", "b@ex.com", "Bia", null));
        when(verifier.verify(eq("tok-a")))
                .thenReturn(new VerifiedToken("uid-a", "a@ex.com", "Ana", null));
        when(verifier.verify(eq("tok-b")))
                .thenReturn(new VerifiedToken("uid-b", "b@ex.com", "Bia", null));

        // Como a migração grava um pet do app em modo Firestore.
        Pet rex = new Pet();
        rex.setTutorId(tutorA);
        rex.setName("Rex");
        rex.setLegacyFirestoreId(FIRESTORE_ID);
        rex.setLegacyImport(true);
        rex.setPublicId(java.util.UUID.nameUUIDFromBytes(("pet:" + FIRESTORE_ID).getBytes()).toString());
        Pet saved = pets.save(rex);
        petId = saved.getId();
        publicIdOriginal = saved.getPublicId();
    }

    private MvcResult regenerar(String token, String rota, String esperado) throws Exception {
        return mvc.perform(post(rota)
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"expectedPublicId\":\"" + esperado + "\"}"))
                .andReturn();
    }

    private String publicIdDe(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString()).get("publicId").asText();
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(get("/api/v1/legacy-pets/" + FIRESTORE_ID + "/qr")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/legacy-pets/" + FIRESTORE_ID + "/qr/rotation")
                        .contentType("application/json")
                        .content("{\"expectedPublicId\":\"" + publicIdOriginal + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tutorConsultaOQrAtualPeloIdDoFirestoreEPeloIdDaApi() throws Exception {
        mvc.perform(get("/api/v1/legacy-pets/" + FIRESTORE_ID + "/qr").header("Authorization", "Bearer tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(publicIdOriginal))
                .andExpect(jsonPath("$.rotatedAt").doesNotExist());
        mvc.perform(get("/api/v1/pets/" + petId + "/qr").header("Authorization", "Bearer tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value(publicIdOriginal));
    }

    @Test
    void qrAntigoFuncionaAntesEDeixaDeFuncionarDepoisDaRegeneracao() throws Exception {
        mvc.perform(get("/api/v1/public/pets/" + publicIdOriginal)).andExpect(status().isOk());

        MvcResult r = regenerar("tok-a", "/api/v1/legacy-pets/" + FIRESTORE_ID + "/qr/rotation", publicIdOriginal);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        String novo = publicIdDe(r);

        assertThat(novo).isNotEqualTo(publicIdOriginal);
        assertThat(java.util.UUID.fromString(novo).version()).isEqualTo(4);
        mvc.perform(get("/api/v1/public/pets/" + novo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rex"));
        mvc.perform(get("/api/v1/public/pets/" + publicIdOriginal)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/public/pets/" + publicIdOriginal + "/sightings")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());

        Pet salvo = pets.findById(petId).orElseThrow();
        assertThat(salvo.getPublicIdRotations()).isEqualTo(1);
        assertThat(salvo.getPublicIdRotatedAt()).isNotNull();
    }

    @Test
    void regeneraPeloIdDaApiTambem() throws Exception {
        MvcResult r = regenerar("tok-a", "/api/v1/pets/" + petId + "/qr/rotation", publicIdOriginal);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(pets.findById(petId).orElseThrow().getPublicId()).isEqualTo(publicIdDe(r));
    }

    @Test
    void outroTutorNaoConsultaNemRegeneraEOQrContinuaOMesmo() throws Exception {
        mvc.perform(get("/api/v1/legacy-pets/" + FIRESTORE_ID + "/qr").header("Authorization", "Bearer tok-b"))
                .andExpect(status().isNotFound());
        assertThat(regenerar("tok-b", "/api/v1/legacy-pets/" + FIRESTORE_ID + "/qr/rotation", publicIdOriginal)
                .getResponse().getStatus()).isEqualTo(404);
        assertThat(regenerar("tok-b", "/api/v1/pets/" + petId + "/qr/rotation", publicIdOriginal)
                .getResponse().getStatus()).isEqualTo(404);

        assertThat(pets.findById(petId).orElseThrow().getPublicId()).isEqualTo(publicIdOriginal);
        mvc.perform(get("/api/v1/public/pets/" + publicIdOriginal)).andExpect(status().isOk());
    }

    @Test
    void petAindaNaoMigradoRetorna404() throws Exception {
        mvc.perform(get("/api/v1/legacy-pets/nao-migrado/qr").header("Authorization", "Bearer tok-a"))
                .andExpect(status().isNotFound());
    }

    @Test
    void qrEsperadoDesatualizadoRetorna409SemTrocarDeNovo() throws Exception {
        String novo = publicIdDe(regenerar("tok-a",
                "/api/v1/legacy-pets/" + FIRESTORE_ID + "/qr/rotation", publicIdOriginal));

        MvcResult repetido = regenerar("tok-a",
                "/api/v1/legacy-pets/" + FIRESTORE_ID + "/qr/rotation", publicIdOriginal);
        assertThat(repetido.getResponse().getStatus()).isEqualTo(409);
        assertThat(pets.findById(petId).orElseThrow().getPublicId()).isEqualTo(novo);
    }

    @Test
    void expectedPublicIdObrigatorio() throws Exception {
        mvc.perform(post("/api/v1/legacy-pets/" + FIRESTORE_ID + "/qr/rotation")
                        .header("Authorization", "Bearer tok-a")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pedidosSimultaneosGeramUmSoQrAtivo() throws Exception {
        int n = 4;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<MvcResult>> futuros = new ArrayList<>();
        try {
            for (int i = 0; i < n; i++) {
                Callable<MvcResult> tarefa = () -> {
                    largada.await();
                    return regenerar("tok-a", "/api/v1/legacy-pets/" + FIRESTORE_ID + "/qr/rotation",
                            publicIdOriginal);
                };
                futuros.add(pool.submit(tarefa));
            }
            largada.countDown();

            List<String> vencedores = new ArrayList<>();
            int conflitos = 0;
            for (Future<MvcResult> f : futuros) {
                MvcResult r = f.get();
                if (r.getResponse().getStatus() == 200) {
                    vencedores.add(publicIdDe(r));
                } else {
                    assertThat(r.getResponse().getStatus()).isEqualTo(409);
                    conflitos++;
                }
            }

            assertThat(vencedores).hasSize(1);
            assertThat(conflitos).isEqualTo(n - 1);
            Pet salvo = pets.findById(petId).orElseThrow();
            assertThat(salvo.getPublicId()).isEqualTo(vencedores.get(0));
            assertThat(salvo.getPublicIdRotations()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void limitaRegeneracoesPorPet() throws Exception {
        String atual = publicIdOriginal;
        for (int i = 0; i < 5; i++) {
            MvcResult r = regenerar("tok-a", "/api/v1/legacy-pets/" + FIRESTORE_ID + "/qr/rotation", atual);
            assertThat(r.getResponse().getStatus()).isEqualTo(200);
            atual = publicIdDe(r);
        }

        MvcResult bloqueado = regenerar("tok-a", "/api/v1/legacy-pets/" + FIRESTORE_ID + "/qr/rotation", atual);
        assertThat(bloqueado.getResponse().getStatus()).isEqualTo(429);
        assertThat(pets.findById(petId).orElseThrow().getPublicId()).isEqualTo(atual);
        mvc.perform(get("/api/v1/public/pets/" + atual)).andExpect(status().isOk());
    }
}
