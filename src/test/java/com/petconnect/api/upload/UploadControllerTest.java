package com.petconnect.api.upload;

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

import static org.mockito.ArgumentMatchers.eq;
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
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(post("/api/v1/uploads/signature")).andExpect(status().isUnauthorized());
    }

    @Test
    void assinaturaDevolveOsCamposParaOUploadDireto() throws Exception {
        mvc.perform(post("/api/v1/uploads/signature").header("Authorization", "Bearer tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").value("test-api-key"))
                .andExpect(jsonPath("$.cloudName").value("test-cloud"))
                .andExpect(jsonPath("$.signature").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNumber());
    }

    @Test
    void deleteChamaOCloudinaryComResourceTypeEPublicIdCorretos() throws Exception {
        when(cloudinaryClient.destroy(eq("image"), eq("n8xharlgwf5jmmrjcejj"))).thenReturn(true);

        mvc.perform(delete("/api/v1/uploads").header("Authorization", "Bearer tok-a")
                        .contentType("application/json")
                        .content("{\"url\":\"https://res.cloudinary.com/qgrx3f8s/image/upload/v1787161823/n8xharlgwf5jmmrjcejj.jpg\"}"))
                .andExpect(status().isNoContent());

        verify(cloudinaryClient).destroy("image", "n8xharlgwf5jmmrjcejj");
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
