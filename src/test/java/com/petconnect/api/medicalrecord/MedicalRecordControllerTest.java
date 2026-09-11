package com.petconnect.api.medicalrecord;

import com.petconnect.api.medicalrecord.infrastructure.MedicalRecordRepository;
import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.pet.infrastructure.PetRepository;
import com.petconnect.api.shared.security.FirebaseTokenVerifier;
import com.petconnect.api.shared.security.VerifiedToken;
import com.petconnect.api.user.domain.User;
import com.petconnect.api.user.infrastructure.UserRepository;
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
class MedicalRecordControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    PetRepository pets;
    @Autowired
    MedicalRecordRepository records;

    @MockitoBean
    FirebaseTokenVerifier verifier;

    private String petA;
    private String petB;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        pets.deleteAll();
        records.deleteAll();
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
        return "/api/v1/pets/" + petId + "/medical-records";
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(get(base(petA))).andExpect(status().isUnauthorized());
    }

    @Test
    void criaComIdPreGeradoPeloAppEPersisteComEsseId() throws Exception {
        mvc.perform(post(base(petA)).header("Authorization", "Bearer tok-a")
                        .contentType("application/json")
                        .content("{\"id\":\"client-uuid-123\",\"recordedAt\":\"2025-04-01\","
                                + "\"description\":\"Exame de sangue\",\"attachments\":[\"https://res.cloudinary.com/x/a.pdf\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("client-uuid-123"))
                .andExpect(jsonPath("$.origin").value("TUTOR"))
                .andExpect(jsonPath("$.attachments", hasSize(1)));

        assertThat(records.findById("client-uuid-123")).isPresent();
    }

    @Test
    void criaSemIdDeixaOMongoGerar() throws Exception {
        String body = mvc.perform(post(base(petA)).header("Authorization", "Bearer tok-a")
                        .contentType("application/json")
                        .content("{\"recordedAt\":\"2025-04-01\",\"description\":\"Consulta de rotina\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$.id");
        assertThat(id).isNotBlank();
    }

    @Test
    void idDuplicadoRetorna409() throws Exception {
        mvc.perform(post(base(petA)).header("Authorization", "Bearer tok-a")
                        .contentType("application/json")
                        .content("{\"id\":\"dup-1\",\"recordedAt\":\"2025-04-01\",\"description\":\"x\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post(base(petA)).header("Authorization", "Bearer tok-a")
                        .contentType("application/json")
                        .content("{\"id\":\"dup-1\",\"recordedAt\":\"2025-04-02\",\"description\":\"y\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void listaEmOrdemCronologicaEEditaPreservandoOId() throws Exception {
        criarA("{\"recordedAt\":\"2025-05-01\",\"description\":\"Recente\"}");
        criarA("{\"recordedAt\":\"2025-01-01\",\"description\":\"Antigo\"}");

        String body = mvc.perform(get(base(petA)).header("Authorization", "Bearer tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].description").value("Antigo"))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(body, "$[0].id");

        mvc.perform(patch(base(petA) + "/" + id).header("Authorization", "Bearer tok-a")
                        .contentType("application/json")
                        .content("{\"id\":\"tentando-trocar-o-id\",\"recordedAt\":\"2025-01-02\",\"description\":\"Antigo (editado)\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.description").value("Antigo (editado)"));
    }

    @Test
    void excluiRegistro() throws Exception {
        String id = criarEPegarId(petA, "{\"recordedAt\":\"2025-01-01\",\"description\":\"x\"}");
        mvc.perform(delete(base(petA) + "/" + id).header("Authorization", "Bearer tok-a"))
                .andExpect(status().isNoContent());
        assertThat(records.findById(id)).isEmpty();
    }

    @Test
    void naoAcessaRegistroDePetDeOutroTutor() throws Exception {
        String recB = criarEPegarId(petB, "{\"recordedAt\":\"2025-01-01\",\"description\":\"x\"}");

        mvc.perform(get(base(petB)).header("Authorization", "Bearer tok-a"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(base(petB) + "/" + recB).header("Authorization", "Bearer tok-a"))
                .andExpect(status().isNotFound());
        assertThat(records.findById(recB)).isPresent();
    }

    @Test
    void postSemCamposObrigatoriosRetorna400() throws Exception {
        mvc.perform(post(base(petA)).header("Authorization", "Bearer tok-a")
                        .contentType("application/json").content("{\"veterinarian\":\"Dr. X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void excluirPetApagaSeuHistoricoEmCascata() throws Exception {
        criarA("{\"recordedAt\":\"2025-01-01\",\"description\":\"x\"}");
        assertThat(records.findByPetIdOrderByRecordedAtAsc(petA)).hasSize(1);

        mvc.perform(delete("/api/v1/pets/" + petA).header("Authorization", "Bearer tok-a"))
                .andExpect(status().isNoContent());

        assertThat(records.findByPetIdOrderByRecordedAtAsc(petA)).isEmpty();
    }

    private void criarA(String json) throws Exception {
        mvc.perform(post(base(petA)).header("Authorization", "Bearer tok-a")
                        .contentType("application/json").content(json))
                .andExpect(status().isCreated());
    }

    private String criarEPegarId(String petId, String json) throws Exception {
        String bearer = petId.equals(petB) ? "Bearer tok-b" : "Bearer tok-a";
        String body = mvc.perform(post(base(petId)).header("Authorization", bearer)
                        .contentType("application/json").content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }
}
