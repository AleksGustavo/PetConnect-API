package com.petconnect.api.vaccine;

import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.pet.infrastructure.PetRepository;
import com.petconnect.api.shared.security.FirebaseTokenVerifier;
import com.petconnect.api.shared.security.VerifiedToken;
import com.petconnect.api.user.domain.User;
import com.petconnect.api.user.infrastructure.UserRepository;
import com.petconnect.api.vaccine.infrastructure.VaccineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VaccineControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    PetRepository pets;
    @Autowired
    VaccineRepository vaccines;

    @MockitoBean
    FirebaseTokenVerifier verifier;

    private String petA;   // pet da Ana
    private String petB;   // pet da Bia

    @BeforeEach
    void setUp() {
        users.deleteAll();
        pets.deleteAll();
        vaccines.deleteAll();
        String tutorA = users.save(User.provision("uid-a", "a@ex.com", "Ana", null)).getId();
        String tutorB = users.save(User.provision("uid-b", "b@ex.com", "Bia", null)).getId();
        petA = savePet(tutorA, "Rex");
        petB = savePet(tutorB, "Bidu");
        when(verifier.verify(eq("tok-a"))).thenReturn(new VerifiedToken("uid-a", "a@ex.com", "Ana", null));
        when(verifier.verify(eq("tok-b"))).thenReturn(new VerifiedToken("uid-b", "b@ex.com", "Bia", null));
    }

    private String savePet(String tutorId, String name) {
        Pet p = new Pet();
        p.setTutorId(tutorId);
        p.setName(name);
        return pets.save(p).getId();
    }

    private String base(String petId) {
        return "/api/v1/pets/" + petId + "/vaccines";
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(get(base(petA))).andExpect(status().isUnauthorized());
    }

    @Test
    void crudCompletoEmOrdemCronologica() throws Exception {
        criar(petA, "{\"name\":\"Antirrábica\",\"appliedAt\":\"2025-03-10\",\"nextDoseAt\":\"2026-03-10\"}");
        criar(petA, "{\"name\":\"V8\",\"appliedAt\":\"2024-11-01\"}");

        mvc.perform(get(base(petA)).header("Authorization", "Bearer tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].name").value("V8"))          // 2024-11 antes
                .andExpect(jsonPath("$[1].name").value("Antirrábica"));

        String id = com.jayway.jsonpath.JsonPath.read(
                mvc.perform(post(base(petA)).header("Authorization", "Bearer tok-a")
                                .contentType("application/json")
                                .content("{\"name\":\"Giárdia\",\"appliedAt\":\"2025-06-01\"}"))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString(),
                "$.id");

        mvc.perform(patch(base(petA) + "/" + id).header("Authorization", "Bearer tok-a")
                        .contentType("application/json")
                        .content("{\"name\":\"Giárdia (reforço)\",\"appliedAt\":\"2025-06-02\",\"veterinarian\":\"Dr. X\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Giárdia (reforço)"))
                .andExpect(jsonPath("$.veterinarian").value("Dr. X"));

        mvc.perform(delete(base(petA) + "/" + id).header("Authorization", "Bearer tok-a"))
                .andExpect(status().isNoContent());
        mvc.perform(get(base(petA)).header("Authorization", "Bearer tok-a"))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void naoAcessaVacinasDePetDeOutroTutor() throws Exception {
        criar(petB, "{\"name\":\"V10\",\"appliedAt\":\"2025-01-01\"}");
        String vacId = vaccines.findByPetIdOrderByAppliedAtAsc(petB).get(0).getId();

        // Ana tentando pela rota do pet da Bia
        mvc.perform(get(base(petB)).header("Authorization", "Bearer tok-a"))
                .andExpect(status().isNotFound());
        mvc.perform(post(base(petB)).header("Authorization", "Bearer tok-a")
                        .contentType("application/json").content("{\"name\":\"x\",\"appliedAt\":\"2025-01-01\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(base(petB) + "/" + vacId).header("Authorization", "Bearer tok-a"))
                .andExpect(status().isNotFound());

        assertThat(vaccines.findById(vacId)).isPresent();
    }

    @Test
    void vacinaDeOutroPetDoMesmoTutorNaoCasaNaRota() throws Exception {
        // cria um 2º pet da Ana e uma vacina nele
        String tutorA = users.findByFirebaseUid("uid-a").orElseThrow().getId();
        String petA2 = savePet(tutorA, "Toto");
        criar(petA2, "{\"name\":\"V8\",\"appliedAt\":\"2025-02-02\"}");
        String vacId = vaccines.findByPetIdOrderByAppliedAtAsc(petA2).get(0).getId();

        // usar o vacId sob a rota do petA (outro pet do mesmo tutor) → 404
        mvc.perform(patch(base(petA) + "/" + vacId).header("Authorization", "Bearer tok-a")
                        .contentType("application/json").content("{\"name\":\"x\",\"appliedAt\":\"2025-02-02\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void postSemCamposObrigatoriosRetorna400() throws Exception {
        mvc.perform(post(base(petA)).header("Authorization", "Bearer tok-a")
                        .contentType("application/json").content("{\"veterinarian\":\"Dr. X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void excluirPetApagaSuasVacinasEmCascata() throws Exception {
        criar(petA, "{\"name\":\"V8\",\"appliedAt\":\"2025-01-01\"}");
        assertThat(vaccines.findByPetIdOrderByAppliedAtAsc(petA)).hasSize(1);

        mvc.perform(delete("/api/v1/pets/" + petA).header("Authorization", "Bearer tok-a"))
                .andExpect(status().isNoContent());

        assertThat(vaccines.findByPetIdOrderByAppliedAtAsc(petA)).isEmpty();
    }

    private void criar(String petId, String json) throws Exception {
        mvc.perform(post(base(petId)).header("Authorization",
                        petId.equals(petB) ? "Bearer tok-b" : "Bearer tok-a")
                        .contentType("application/json").content(json))
                .andExpect(status().isCreated());
    }
}
