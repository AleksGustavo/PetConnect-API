package com.petconnect.api.shared.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testa o {@link PublicEndpointRateLimitFilter} com limites bem baixos
 * (via @TestPropertySource, que força um contexto Spring separado do resto
 * da suíte — não interfere nos limites generosos de teste em
 * {@code src/test/resources/application.yml}). O status exato da resposta
 * (200/404) não importa aqui: o filtro age antes do controller, então mesmo
 * um 404 já consome a cota.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "petconnect.rate-limit.public-read-per-minute=3",
        "petconnect.rate-limit.public-write-per-minute=2"
})
class PublicEndpointRateLimitFilterTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    RateLimiter limiter;

    @BeforeEach
    void setUp() {
        // Isola cada teste: sem isto, contadores de um método vazariam pro
        // próximo (mesmo IP de loopback do MockMvc em todos eles).
        limiter.clear();
    }

    @Test
    void leituraDentroDoLimitePassaEExcedenteRetorna429() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/v1/public/pets/nao-existe"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/public/pets/nao-existe"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void escritaDentroDoLimitePassaEExcedenteRetorna429() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/v1/public/pets/nao-existe/sightings")
                            .contentType("application/json").content("{}"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/v1/public/pets/nao-existe/sightings")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    @Test
    void limiteDeLeituraEEscritaSaoIndependentes() throws Exception {
        // Estoura o de leitura...
        for (int i = 0; i < 3; i++) {
            mvc.perform(get("/api/v1/public/pets/nao-existe")).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/public/pets/nao-existe")).andExpect(status().isTooManyRequests());

        // ...mas escrita continua livre, porque é uma chave separada.
        mvc.perform(post("/api/v1/public/pets/nao-existe/sightings")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rotaNaoPublicaNaoEAfetadaPeloLimite() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/v1/ping")).andExpect(status().isOk());
        }
    }
}
