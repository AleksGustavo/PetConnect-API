package com.petconnect.api.sharing.application;

import com.petconnect.api.shared.error.ApiException;

import java.util.Set;

/**
 * Telefone de contato da página pública em E.164 ({@code +5519999990000}).
 * Sem {@code +}, o número é tratado como brasileiro. A mesma regra está no
 * app ({@code telefone_de_contato.dart}). Validar o formato não diz se o
 * número tem WhatsApp: isso só o próprio WhatsApp sabe.
 */
public final class ContactPhone {

    /** DDDs em uso no Brasil (Anatel). */
    private static final Set<String> DDDS = Set.of(
            "11", "12", "13", "14", "15", "16", "17", "18", "19",
            "21", "22", "24", "27", "28",
            "31", "32", "33", "34", "35", "37", "38",
            "41", "42", "43", "44", "45", "46", "47", "48", "49",
            "51", "53", "54", "55",
            "61", "62", "63", "64", "65", "66", "67", "68", "69",
            "71", "73", "74", "75", "77", "79",
            "81", "82", "83", "84", "85", "86", "87", "88", "89",
            "91", "92", "93", "94", "95", "96", "97", "98", "99");

    private ContactPhone() {
    }

    /** @throws ApiException 400 se o número não for válido */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid();
        }
        String trimmed = raw.trim();
        if (!trimmed.matches("[+0-9 ().-]+")) {
            throw invalid();
        }
        String digits = trimmed.replaceAll("\\D", "");
        if (!trimmed.startsWith("+")) {
            // Número nacional tem no máximo 11 dígitos; com 12 ou 13 começando
            // por 55, o código do Brasil já veio. Senão, tira o 0 de discagem
            // e acrescenta o 55.
            digits = digits.replaceFirst("^0+", "");
            if (!(digits.length() >= 12 && digits.startsWith("55"))) {
                digits = "55" + digits;
            }
        }
        if (digits.startsWith("55")) {
            return "+" + brazilian(digits.substring(2));
        }
        if (!digits.matches("[1-9][0-9]{7,14}")) {
            throw invalid();
        }
        return "+" + digits;
    }

    private static String brazilian(String national) {
        boolean mobile = national.length() == 11 && national.charAt(2) == '9';
        boolean landline = national.length() == 10 && "2345".indexOf(national.charAt(2)) >= 0;
        if (!(mobile || landline) || !DDDS.contains(national.substring(0, 2))) {
            throw invalid();
        }
        return "55" + national;
    }

    private static ApiException invalid() {
        return ApiException.badRequest("Telefone de contato inválido. Use DDD e número, por exemplo (19) 99999-0000.");
    }
}
