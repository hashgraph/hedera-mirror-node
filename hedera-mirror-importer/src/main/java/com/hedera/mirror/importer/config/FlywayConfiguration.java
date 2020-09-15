package com.hedera.mirror.importer.config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.JavaMigration;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.hedera.mirror.importer.db.DBProperties;

@Configuration
@Log4j2
@RequiredArgsConstructor
public class FlywayConfiguration {

    private final DataSource dataSource;
    private final DBProperties dbProperties;
    private final ApplicationContext applicationContext;

    @Bean
    FlywayMigrationInitializer flywayInitializer() {
        FluentConfiguration config = Flyway.configure()
                .dataSource(dataSource)
                .baselineOnMigrate(true)
                .baselineVersion(dbProperties.getFlywayBaseline())
                .baselineDescription("TimescaleDB initial")
                .javaMigrations(applicationContext.getBeansOfType(JavaMigration.class).values()
                        .toArray(new JavaMigration[0]));
        Flyway flyway = config.load();
        return new FlywayMigrationInitializer(flyway, (f) -> {
            if (!isFlywayInitialized()) {
                log.info("*** flywayInitializer, !isFlywayInitialized...baseline");
                flyway.baseline();
            }
            log.info("** flywayInitializer Running migration");
            flyway.migrate();
        });
    }

    private boolean isFlywayInitialized() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            ResultSet result = metadata.getTables(null, null, "flyway_schema_history", null);
            return result.next();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check if Flyway is initialized", e);
        }
    }
}
