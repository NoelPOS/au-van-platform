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

@Tag("postgres")
public abstract class PostgresTestSupport extends AuthenticationTestSupport {
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

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

    private static String versionOf(Resource migration) {
        String name = migration.getFilename();
        return name.substring(1, name.indexOf("__"));
    }
}
