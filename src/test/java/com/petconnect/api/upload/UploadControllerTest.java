package com.petconnect.api.upload;

import com.jayway.jsonpath.JsonPath;
import com.petconnect.api.shared.security.FirebaseTokenVerifier;
import com.petconnect.api.shared.security.VerifiedToken;
import com.petconnect.api.upload.application.CloudinaryClient;
import com.petconnect.api.user.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UploadControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;

    @MockitoBean
    FirebaseTokenVerifier verifier;
    @MockitoBean
    CloudinaryClient cloudinaryClient;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        when(verifier.verify(eq("tok-a")))
                .thenReturn(new VerifiedToken("uid-a", "a@ex.com", "Ana", null));
        when(verifier.verify(eq("tok-b")))
                .thenReturn(new VerifiedToken("uid-b", "b@ex.com", "Beto", null));
    }

    /** Assina como "tok-a" e devolve o {@code folder} da resposta. */
    private String folderDoTutorA() throws Exception {
        String body = mvc.perform(post("/api/v1/uploads/signature").header("Authorization", "Bearer tok-a"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.folder");
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(post("/api/v1/uploads/signature")).andExpect(status().isUnauthorized());
    }

    @Test
    void assinaturaDevolveOsCamposParaOUploadDiretoIncluindoAPastaDoTutor() throws Exception {
        mvc.perform(post("/api/v1/uploads/signature").header("Authorization", "Bearer tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").value("test-api-key"))
                .andExpect(jsonPath("$.cloudName").value("test-cloud"))
                .andExpect(jsonPath("$.signature").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber())
                .andExpect(jsonPath("$.folder").value(org.hamcrest.Matchers.startsWith("users/")));
    }

    @Test
    void deleteDeArquivoDentroDaPastaDoTutorChamaOCloudinaryComResourceTypeEPublicIdCorretos() throws Exception {
        String folder = folderDoTutorA();
        String publicId = folder + "/n8xharlgwf5jmmrjcejj";
        when(cloudinaryClient.destroy(eq("image"), eq(publicId))).thenReturn(true);

        mvc.perform(delete("/api/v1/uploads").header("Authorization", "Bearer tok-a")
                        .contentType("application/json")
                        .content("{\"url\":\"https://res.cloudinary.com/qgrx3f8s/image/upload/v1787161823/" + publicId + ".jpg\"}"))
                .andExpect(status().isNoContent());

        verify(cloudinaryClient).destroy("image", publicId);
    }

    @Test
    void deleteDeArquivoDeOutroTutorRetorna404ENaoChamaOCloudinary() throws Exception {
        // "b" tenta excluir um arquivo dentro da pasta de "a".
        String folderDoA = folderDoTutorA();
        String publicId = folderDoA + "/n8xharlgwf5jmmrjcejj";

        mvc.perform(delete("/api/v1/uploads").header("Authorization", "Bearer tok-b")
                        .contentType("application/json")
                        .content("{\"url\":\"https://res.cloudinary.com/qgrx3f8s/image/upload/v1787161823/" + publicId + ".jpg\"}"))
                .andExpect(status().isNotFound());

        verify(cloudinaryClient, never()).destroy(anyString(), anyString());
    }

    @Test
    void deleteDeArquivoSemPastaDePreMigracaoRetorna404ENaoChamaOCloudinary() throws Exception {
        // Upload de antes deste endurecimento (sem pasta nenhuma) — não tem
        // como confirmar posse, então nega em vez de assumir que é do tutor.
        mvc.perform(delete("/api/v1/uploads").header("Authorization", "Bearer tok-a")
                        .contentType("application/json")
                        .content("{\"url\":\"https://res.cloudinary.com/qgrx3f8s/image/upload/v1787161823/n8xharlgwf5jmmrjcejj.jpg\"}"))
                .andExpect(status().isNotFound());

        verify(cloudinaryClient, never()).destroy(anyString(), anyString());
    }

    @Test
    void deleteComUrlForaDoPadraoRetorna400() throws Exception {
        mvc.perform(delete("/api/v1/uploads").header("Authorization", "Bearer tok-a")
                        .contentType("application/json")
                        .content("{\"url\":\"https://example.com/x.jpg\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteSemUrlRetorna400() throws Exception {
        mvc.perform(delete("/api/v1/uploads").header("Authorization", "Bearer tok-a")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
