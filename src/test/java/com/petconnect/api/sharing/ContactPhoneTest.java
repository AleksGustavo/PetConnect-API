package com.petconnect.api.sharing;

import com.petconnect.api.shared.error.ApiException;
import com.petconnect.api.sharing.application.ContactPhone;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Telefone de contato da página pública em E.164 (mesmos casos do app). */
class ContactPhoneTest {

    @ParameterizedTest
    @CsvSource({
            "'(19) 99999-0000', +5519999990000",
            "'19999990000', +5519999990000",
            "'019 99999-0000', +5519999990000",
            "'+55 19 99999-0000', +5519999990000",
            "'5519999990000', +5519999990000",
            "'(55) 99999-0000', +5555999990000",
            "'(11) 3333-4444', +551133334444",
            "'+1 415 555 2671', +14155552671",
            "'+351 912 345 678', +351912345678"
    })
    void normalizaParaE164(String entrada, String esperado) {
        assertThat(ContactPhone.normalize(entrada)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", " ", "123", "(19) 8999-00000", "(20) 99999-0000", "(19) 19999-0000",
            "(19) 1333-4444", "+55 19 9999", "+0 123 456 789", "+1234567890123456",
            "19 99999-0000 ramal 2", "tel:19999990000"
    })
    void recusaNumeroInvalido(String entrada) {
        assertThatThrownBy(() -> ContactPhone.normalize(entrada)).isInstanceOf(ApiException.class);
    }
}
