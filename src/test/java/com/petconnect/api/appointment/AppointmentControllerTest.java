package com.petconnect.api.appointment;

import com.petconnect.api.appointment.infrastructure.AppointmentRepository;
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
class AppointmentControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    PetRepository pets;
    @Autowired
    AppointmentRepository appointments;

    @MockitoBean
    FirebaseTokenVerifier verifier;

    private String petA;
    private String petB;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        pets.deleteAll();
        appointments.deleteAll();
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
        return "/api/v1/pets/" + petId + "/appointments";
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(get(base(petA))).andExpect(status().isUnauthorized());
    }

    @Test
    void criaComStatusConfirmedPorDefaultEListaEmOrdem() throws Exception {
        criarA("{\"scheduledDate\":\"2025-06-10\",\"veterinarian\":\"Dra. X\",\"reason\":\"Check-up\"}");
        criarA("{\"scheduledDate\":\"2025-05-01\",\"scheduledTime\":\"09:00:00\",\"veterinarian\":\"Dr. Y\",\"reason\":\"Vacina\"}");

        mvc.perform(get(base(petA)).header("Authorization", "Bearer tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].reason").value("Vacina"))       // 05-01 antes de 06-10
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].scheduledTime").value("09:00:00"))
                .andExpect(jsonPath("$[1].reason").value("Check-up"));
    }

    @Test
    void patchMudaStatusParaCanceladaOuRealizada() throws Exception {
        String id = criarEPegarId(petA,
                "{\"scheduledDate\":\"2025-06-10\",\"veterinarian\":\"Dra. X\",\"reason\":\"Check-up\"}");

        mvc.perform(patch(base(petA) + "/" + id).header("Authorization", "Bearer tok-a")
                        .contentType("application/json")
                        .content("{\"scheduledDate\":\"2025-06-10\",\"veterinarian\":\"Dra. X\",\"reason\":\"Check-up\",\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mvc.perform(patch(base(petA) + "/" + id).header("Authorization", "Bearer tok-a")
                        .contentType("application/json")
                        .content("{\"scheduledDate\":\"2025-06-10\",\"veterinarian\":\"Dra. X\",\"reason\":\"Check-up\",\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void editarSemEnviarStatusMantemOAtual() throws Exception {
        String id = criarEPegarId(petA,
                "{\"scheduledDate\":\"2025-06-10\",\"veterinarian\":\"Dra. X\",\"reason\":\"Check-up\",\"status\":\"CANCELLED\"}");

        mvc.perform(patch(base(petA) + "/" + id).header("Authorization", "Bearer tok-a")
                        .contentType("application/json")
                        .content("{\"scheduledDate\":\"2025-06-11\",\"veterinarian\":\"Dra. X\",\"reason\":\"Retorno\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Retorno"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void naoAcessaConsultaDePetDeOutroTutor() throws Exception {
        String vetBId = criarEPegarId(petB, "{\"scheduledDate\":\"2025-01-01\",\"veterinarian\":\"X\",\"reason\":\"Y\"}");

        mvc.perform(get(base(petB)).header("Authorization", "Bearer tok-a"))
                .andExpect(status().isNotFound());
        mvc.perform(patch(base(petB) + "/" + vetBId).header("Authorization", "Bearer tok-a")
                        .contentType("application/json")
                        .content("{\"scheduledDate\":\"2025-01-01\",\"veterinarian\":\"X\",\"reason\":\"Y\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void postSemCamposObrigatoriosRetorna400() throws Exception {
        mvc.perform(post(base(petA)).header("Authorization", "Bearer tok-a")
                        .contentType("application/json").content("{\"veterinarian\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void naoExisteEndpointDeDelete() throws Exception {
        String id = criarEPegarId(petA, "{\"scheduledDate\":\"2025-01-01\",\"veterinarian\":\"X\",\"reason\":\"Y\"}");
        mvc.perform(delete(base(petA) + "/" + id).header("Authorization", "Bearer tok-a"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void excluirPetApagaSuasConsultasEmCascata() throws Exception {
        criarA("{\"scheduledDate\":\"2025-01-01\",\"veterinarian\":\"X\",\"reason\":\"Y\"}");
        assertThat(appointments.findByPetIdOrderByScheduledDateAscScheduledTimeAsc(petA)).hasSize(1);

        mvc.perform(delete("/api/v1/pets/" + petA).header("Authorization", "Bearer tok-a"))
                .andExpect(status().isNoContent());

        assertThat(appointments.findByPetIdOrderByScheduledDateAscScheduledTimeAsc(petA)).isEmpty();
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
