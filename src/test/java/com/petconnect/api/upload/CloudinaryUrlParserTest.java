package com.petconnect.api.upload;

import com.petconnect.api.shared.error.ApiException;
import com.petconnect.api.upload.application.CloudinaryUrlParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudinaryUrlParserTest {

    @Test
    void extraiResourceTypeEPublicIdDeUmaFotoReal() {
        var parsed = CloudinaryUrlParser.parse(
                "https://res.cloudinary.com/qgrx3f8s/image/upload/v1787161823/n8xharlgwf5jmmrjcejj.jpg");

        assertThat(parsed.resourceType()).isEqualTo("image");
        assertThat(parsed.publicId()).isEqualTo("n8xharlgwf5jmmrjcejj");
    }

    @Test
    void reconhecePublicIdComPastas() {
        var parsed = CloudinaryUrlParser.parse(
                "https://res.cloudinary.com/qgrx3f8s/raw/upload/v123/pets/abc/exame.pdf");

        assertThat(parsed.resourceType()).isEqualTo("raw");
        assertThat(parsed.publicId()).isEqualTo("pets/abc/exame");
    }

    @Test
    void urlForaDoPadraoLanca400() {
        assertThatThrownBy(() -> CloudinaryUrlParser.parse("https://example.com/foto.jpg"))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void urlNulaLanca400() {
        assertThatThrownBy(() -> CloudinaryUrlParser.parse(null))
                .isInstanceOf(ApiException.class);
    }
}
