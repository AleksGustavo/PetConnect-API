package com.petconnect.api.shared.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

/** Endpoint público de verificação — confirma que a API está no ar. */
@RestController
@RequestMapping(ApiV1.BASE + "/ping")
@Tag(name = "infra")
public class PingController {

    @GetMapping
    @Operation(summary = "Liveness público da API")
    public Map<String, Object> ping() {
        return Map.of(
                "status", "ok",
                "service", "petconnect-api",
                "time", OffsetDateTime.now().toString());
    }
}
