package com.petconnect.api.vaccine.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Corpo do {@code POST} e do {@code PATCH} de vacina. O app sempre envia o
 * registro completo, então {@code name} e {@code appliedAt} são exigidos nos
 * dois; os demais campos podem vir nulos (limpam o valor).
 */
public record VaccineRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull LocalDate appliedAt,
        LocalDate nextDoseAt,
        @Size(max = 120) String veterinarian,
        @Size(max = 2000) String notes
) {
}
