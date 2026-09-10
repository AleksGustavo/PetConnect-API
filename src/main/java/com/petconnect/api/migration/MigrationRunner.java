package com.petconnect.api.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

/**
 * Executa a migração quando o perfil {@code migration} está ativo:
 *
 * <pre>
 * mvn spring-boot:run -Dspring-boot.run.profiles=dev,migration \
 *     -Dspring-boot.run.arguments=--petconnect.migration.source=C:/.../firestore_backup.json
 * </pre>
 *
 * Grava {@code migration-report-<timestamp>.json} ao lado do arquivo de origem.
 */
@Component
@Profile("migration")
@Order(100)
public class MigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    private final LegacyMigrationService service;
    private final MigrationProperties props;
    private final ObjectMapper objectMapper;
    private final ConfigurableApplicationContext context;

    public MigrationRunner(LegacyMigrationService service, MigrationProperties props,
                           ObjectMapper objectMapper, ConfigurableApplicationContext context) {
        this.service = service;
        this.props = props;
        this.objectMapper = objectMapper;
        this.context = context;
    }

    @Override
    public void run(String... args) throws Exception {
        if (props.source() == null || props.source().isBlank()) {
            throw new IllegalStateException(
                    "Defina petconnect.migration.source com o caminho do firestore_backup.json.");
        }
        Path source = Path.of(props.source());
        if (!Files.isRegularFile(source)) {
            throw new IllegalStateException("Arquivo não encontrado: " + source.toAbsolutePath());
        }

        log.info("Lendo export do Firestore: {}", source.toAbsolutePath());
        JsonNode root = objectMapper.readTree(Files.readAllBytes(source));

        MigrationReport report = service.migrate(root, source.getFileName().toString());

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path out = source.resolveSibling("migration-report-" + stamp + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), report);
        log.info("Relatório salvo em {}", out.toAbsolutePath());
        log.info("Resumo: users {}·{} pets {}·{} locations {}·{}  (migrados·total)",
                report.users().migrated(), report.users().total(),
                report.pets().migrated(), report.pets().total(),
                report.locations().migrated(), report.locations().total());

        if (props.shouldExit()) {
            int code = SpringApplicationExit.exit(context);
            if (code != 0) {
                log.warn("Aplicação encerrou com código {}", code);
            }
        }
    }

    /** Isolado para não acoplar o teste ao ciclo de vida do contexto. */
    static final class SpringApplicationExit {
        private SpringApplicationExit() {
        }

        static int exit(ConfigurableApplicationContext ctx) {
            return org.springframework.boot.SpringApplication.exit(ctx, () -> 0);
        }
    }
}
