package com.petconnect.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.mongodb.config.EnableMongoAuditing;

/**
 * Ponto de entrada da API do PetConnect.
 *
 * <p>Arquitetura: monólito modular. Cada pacote sob {@code com.petconnect.api}
 * (user, pet, vaccine, appointment, medicalrecord, location) é um módulo
 * independente; {@code shared} concentra configuração transversal (segurança,
 * tratamento de erro, OpenAPI).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableMongoAuditing
public class PetConnectApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PetConnectApiApplication.class, args);
    }
}
