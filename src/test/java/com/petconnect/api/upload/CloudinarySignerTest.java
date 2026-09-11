package com.petconnect.api.upload;

import com.petconnect.api.upload.application.CloudinarySigner;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CloudinarySignerTest {

    // Vetores conferidos com uma implementação de referência (Node crypto):
    // sha1("timestamp=1700000000" + secret) e
    // sha1("public_id=...&timestamp=..." + secret) — ordem alfabética dos parâmetros.

    @Test
    void assinaSoTimestamp() {
        String sig = CloudinarySigner.sign(Map.of("timestamp", "1700000000"), "test_secret_123");
        assertThat(sig).isEqualTo("c23f14cbef82b943cc2338874aecd9218e0de163");
    }

    @Test
    void ordenaParametrosAlfabeticamenteAntesDeAssinar() {
        String sig1 = CloudinarySigner.sign(
                Map.of("public_id", "n8xharlgwf5jmmrjcejj", "timestamp", "1700000000"), "test_secret_123");
        String sig2 = CloudinarySigner.sign(
                Map.of("timestamp", "1700000000", "public_id", "n8xharlgwf5jmmrjcejj"), "test_secret_123");

        assertThat(sig1).isEqualTo(sig2);
        assertThat(sig1).isEqualTo("5fa189c330df3b725b3d1750c62a5405432c6d97");
    }

    @Test
    void segredoDiferenteGeraAssinaturaDiferente() {
        String a = CloudinarySigner.sign(Map.of("timestamp", "1"), "secret-a");
        String b = CloudinarySigner.sign(Map.of("timestamp", "1"), "secret-b");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void assinaturaTemSempre40CaracteresHex() {
        String sig = CloudinarySigner.sign(Map.of("timestamp", "42"), "qualquer-coisa");
        assertThat(sig).hasSize(40).matches("[0-9a-f]{40}");
    }
}
