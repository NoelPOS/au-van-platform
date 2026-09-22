package com.auvan.api;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real PostgreSQL server the {@code @Tag("postgres")} suite runs against,
 * and the two checks that say it really is one.
 *
 * <p><strong>One container for the whole suite.</strong> The field is static and
 * started once in this class's initialiser, so every class extending this shares
 * the same server however many Spring contexts the suite ends up with. Deliberately
 * <em>not</em> {@code @Testcontainers} with {@code @Container}: that extension owns
 * a container per class, and paying {@code postgres:17-alpine}'s start-up for each
 * of them is the difference between a CI job that is kept and one that is switched
 * off for being slow. Ryuk removes it when the JVM exits.
 *
 * <p>The tag lives here rather than on each subclass: it is what keeps the default
 * {@code ./gradlew test} task Docker-free, and a new class that forgets it would
 * silently put Docker on the critical path of every build.
 *
 * <p>The image is pinned to the tag {@code compose.yaml} runs, so this proves the
 * behaviour of the server local development and the demo actually use.
 *
 * <p>Extends {@link AuthenticationTestSupport} rather than repeating its JWT
 * property: the API refuses to start without a signing key.
 *
 * <p>Schema: {@code spring.jpa.hibernate.ddl-auto} stays {@code validate} and
 * Flyway builds the schema from the real migration history. Hibernate therefore
 * refuses to start a context here at all unless the entity mappings agree with the
 * migrations <em>on PostgreSQL</em>, which is coverage every class below gets for
 * free — {@code DatabaseMigrationIntegrationTests}' real-engine sibling.
 */
@Tag("postgres")
public abstract class PostgresTestSupport extends AuthenticationTestSupport {
    /**
     * {@code @ServiceConnection} is what overrides the H2 datasource in
     * {@code src/test/resources/application.yml}. That file replaces the main
     * configuration outright rather than merging with it, so without this every
     * class below would quietly run on {@code jdbc:h2:mem:au_van} and pass for the
     * wrong reason — which is what the first test in this class exists to catch.
     */
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * The check that catches the most likely way this whole suite could be quietly
     * broken. A misconfiguration that fell back to the H2 datasource would look
     * exactly like a pass, and every PostgreSQL-specific property the classes below
     * assert would be asserting nothing.
     *
     * <p>The isolation level is asserted for the same reason: READ COMMITTED is the
     * premise of every test here — the blocking re-read behind
     * {@code SELECT … FOR UPDATE} and the {@code WHERE}-clause recheck behind the
     * outbox claim are both properties of it, and neither means anything under a
     * different level.
     *
     * <p>Inherited, so it runs once per subclass. That is the point: each subclass
     * may build its own Spring context, and each of those contexts has to be proven
     * separately.
     */
    @Test
    void theDatasourceIsTheRealPostgresContainerAndNotTheH2FallbackInTheTestConfiguration() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            assertThat(metaData.getURL()).startsWith("jdbc:postgresql:");
            assertThat(metaData.getDatabaseProductName()).isEqualTo("PostgreSQL");
            assertThat(metaData.getDatabaseMajorVersion()).isEqualTo(17);
            assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
        }
    }

    /**
     * Every migration in the repository applied cleanly to an empty PostgreSQL 17,
     * and none of them failed. Discovered from the classpath rather than listed, so
     * a migration added later is covered the moment it lands.
     */
    @Test
    void flywayAppliedTheWholeMigrationHistoryToTheRealServer() throws IOException {
        Resource[] onDisk = new PathMatchingResourcePatternResolver()
                .getResources("classpath:db/migration/V*__*.sql");
        List<String> versions = Arrays.stream(onDisk).map(PostgresTestSupport::versionOf).toList();
        assertThat(versions).isNotEmpty();

        List<String> applied = jdbc.queryForList(
                "select version from flyway_schema_history where success = true and version is not null",
                String.class);

        assertThat(applied).containsExactlyInAnyOrderElementsOf(versions);
    }

    /** {@code V7__create_outbox_events.sql} is version {@code 7}, as Flyway records it. */
    private static String versionOf(Resource migration) {
        String name = migration.getFilename();
        return name.substring(1, name.indexOf("__"));
    }
}
