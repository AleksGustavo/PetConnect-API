package com.petconnect.api.migration.support;

import com.petconnect.api.pet.domain.PetGender;
import com.petconnect.api.pet.domain.PetSize;
import com.petconnect.api.pet.domain.Species;
import com.petconnect.api.user.domain.Gender;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conversores das idiossincrasias do Firestore legado (ver
 * {@code docs/database/firestore-data-report.md}). Sem estado — só funções puras.
 */
public final class LegacyValues {

    private LegacyValues() {
    }

    private static final DateTimeFormatter BR = DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:[.,]\\d+)?");

    /** {@code true} se a string tem conteúdo. */
    public static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    public static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Aceita {@code dd/MM/yyyy} e ISO {@code yyyy-MM-dd}. Datas impossíveis
     * ({@code 1/1/1}, {@code 13/32/321}, {@code 12/12/13}) → {@code null}.
     */
    public static LocalDate parseDate(String raw) {
        String s = trimToNull(raw);
        if (s == null) {
            return null;
        }
        LocalDate d = tryParse(s, BR);
        if (d == null) {
            d = tryParse(s, ISO);
        }
        if (d == null) {
            return null;
        }
        int year = d.getYear();
        if (year < 1900 || year > LocalDate.now().getYear()) {
            return null;
        }
        return d;
    }

    private static LocalDate tryParse(String s, DateTimeFormatter fmt) {
        try {
            return LocalDate.parse(s, fmt);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * {@code peso} legado: número, string numérica, com unidade ({@code "10 KG"},
     * {@code "4kg"}), lixo ({@code "byi"}), vazio ou {@code null}.
     *
     * @return kg como {@code Double}, ou {@code null} se não der para extrair
     */
    public static Double parseWeightKg(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            double v = n.doubleValue();
            return v > 0 ? v : null;
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        Matcher m = NUMBER.matcher(s.replace(',', '.'));
        if (!m.find()) {
            return null;
        }
        try {
            double v = Double.parseDouble(m.group());
            return v > 0 && v < 500 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code Homem}/{@code Mulher}/{@code Outro(s)} → enum; ausente → {@code null}. */
    public static Gender mapUserGender(String raw) {
        String s = normalize(raw);
        if (s == null) {
            return null;
        }
        return switch (s) {
            case "homem", "masculino", "male" -> Gender.MALE;
            case "mulher", "feminino", "female" -> Gender.FEMALE;
            case "outro", "outros", "other" -> Gender.OTHER;
            default -> Gender.UNDISCLOSED;
        };
    }

    /** {@code Macho}/{@code Fêmea} → enum; resto → {@code UNKNOWN}. */
    public static PetGender mapPetGender(String raw) {
        String s = normalize(raw);
        if (s == null) {
            return PetGender.UNKNOWN;
        }
        return switch (s) {
            case "macho", "male", "m" -> PetGender.MALE;
            case "femea", "female", "f" -> PetGender.FEMALE;
            default -> PetGender.UNKNOWN;
        };
    }

    /** Texto livre → {@code DOG}/{@code CAT}/{@code OTHER}. */
    public static Species mapSpecies(String raw) {
        String s = normalize(raw);
        if (s == null) {
            return Species.OTHER;
        }
        if (s.contains("gat") || s.equals("felino")) {
            return Species.CAT;
        }
        if (s.contains("cachorr") || s.contains("cao") || s.contains("dog") || s.contains("canino")) {
            return Species.DOG;
        }
        return Species.OTHER;
    }

    /** {@code pequeno}/{@code médio}/{@code grande} (qualquer caixa) → enum; lixo → {@code null}. */
    public static PetSize mapSize(String raw) {
        String s = normalize(raw);
        if (s == null) {
            return null;
        }
        return switch (s) {
            case "pequeno", "small", "p" -> PetSize.SMALL;
            case "medio", "média", "media", "medium", "m" -> PetSize.MEDIUM;
            case "grande", "large", "g" -> PetSize.LARGE;
            default -> null;
        };
    }

    /** Telefone: colapsa espaços; devolve {@code null} se ficar vazio. */
    public static String cleanPhone(String raw) {
        String s = trimToNull(raw);
        return s == null ? null : s.replaceAll("\\s+", " ");
    }

    /** minúsculas, sem acento, sem espaços nas pontas. */
    private static String normalize(String raw) {
        String s = trimToNull(raw);
        if (s == null) {
            return null;
        }
        String noAccents = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccents.toLowerCase(Locale.ROOT);
    }
}
