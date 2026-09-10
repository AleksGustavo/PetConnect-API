package com.petconnect.api.user;

import com.petconnect.api.shared.security.FirebaseTokenVerifier;
import com.petconnect.api.shared.security.TokenVerificationException;
import com.petconnect.api.shared.security.VerifiedToken;
import com.petconnect.api.user.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MeControllerTest {

    private static final String GOOD = "good-token";
    private static final String BAD = "bad-token";

    @Autowired
    MockMvc mvc;

    @Autowired
    UserRepository users;

    @MockitoBean
    FirebaseTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        when(verifier.verify(eq(GOOD)))
                .thenReturn(new VerifiedToken("uid-123", "joao@example.com", "João Silva", null));
        when(verifier.verify(eq(BAD)))
                .thenThrow(new TokenVerificationException("inválido"));
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void tokenInvalidoRetorna401() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + BAD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void primeiroAcessoProvisionaERetornaOPerfil() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + GOOD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firebaseUid").value("uid-123"))
                .andExpect(jsonPath("$.email").value("joao@example.com"))
                .andExpect(jsonPath("$.firstName").value("João Silva"))
                .andExpect(jsonPath("$.roles[0]").value("TUTOR"));

        assertThat(users.findByFirebaseUid("uid-123")).isPresent();
    }

    @Test
    void acessosRepetidosNaoDuplicamOUsuario() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + GOOD))
                    .andExpect(status().isOk());
        }
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void patchAtualizaOsCampos() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + GOOD))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + GOOD)
                        .contentType("application/json")
                        .content("""
                                {"firstName":"João","lastName":"Silva","phone":"19999998888","gender":"MALE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("João"))
                .andExpect(jsonPath("$.lastName").value("Silva"))
                .andExpect(jsonPath("$.fullName").value("João Silva"))
                .andExpect(jsonPath("$.phone").value("19999998888"))
                .andExpect(jsonPath("$.gender").value("MALE"));
    }

    @Test
    void patchComPayloadInvalidoRetorna400() throws Exception {
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + GOOD))
                .andExpect(status().isOk());

        String longName = "x".repeat(200);
        mvc.perform(patch("/api/v1/me")
                        .header("Authorization", "Bearer " + GOOD)
                        .contentType("application/json")
                        .content("{\"firstName\":\"" + longName + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
