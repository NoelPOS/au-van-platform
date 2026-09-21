package com.auvan.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.util.Objects;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The schema the application runs on comes from {@code db/migration}, not from
 * the entity mappings. Issue #27: {@code flyway-core} sat on the classpath
 * without {@code spring-boot-flyway}, so no Flyway bean existed and none of the
 * migrations had ever been applied — a gap the rest of the suite could not see,
 * because Hibernate was generating an equivalent-looking schema instead.
 */
@SpringBootTest
class DatabaseMigrationIntegrationTests extends AuthenticationTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayRecordsEveryMigrationAsApplied() {
        // Flyway creates its history table and columns with quoted, lower-case
        // names, so the query quotes them too; H2 folds unquoted identifiers to
        // upper case and would not find them otherwise.
        List<String> applied = jdbc.queryForList("""
                select "version" from "flyway_schema_history"
                where "success" and "version" is not null
                order by "installed_rank"
                """, String.class);

        assertThat(applied).containsExactly("1", "2", "3", "4", "5");
    }

    @Test
    void theSchemaCarriesABookingCheckConstraintNoMappingCouldExpress() {
        // This used to query idempotency_keys: nothing mapped it, so Hibernate
        // could not have generated it, and reading it at all was the proof. #25
        // maps that table, so the proof had to move to something a mapping
        // cannot express at all. bookings_total_fare_non_negative is a CHECK
        // clause and exists only in V3.
        //
        // It is read from the catalog rather than provoked, because bookings has
        // two foreign keys and a violation of either would be reported exactly
        // like a violated check clause from here — the test would pass whether
        // or not the constraint existed.
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.table_constraints
                where upper(constraint_name) = 'BOOKINGS_TOTAL_FARE_NON_NEGATIVE'
                  and constraint_type = 'CHECK'
                """, Integer.class)).isOne();
    }

    /**
     * Once Flyway is on the classpath Spring Boot defaults {@code ddl-auto} to
     * {@code none}, so dropping the setting would not turn any other test red —
     * it would quietly stop checking the mappings against the schema. Both
     * configurations are asserted because the test file replaces the main one
     * outright rather than merging with it.
     */
    @Test
    void everyConfigurationValidatesTheMappingsAgainstTheMigratedSchema() throws IOException {
        Resource[] configurations = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:/application.yml");

        assertThat(configurations).hasSize(2);
        for (Resource configuration : configurations) {
            assertThat(ddlAutoIn(configuration)).as("%s", configuration).isEqualTo("validate");
        }
    }

    private Object ddlAutoIn(Resource configuration) throws IOException {
        // Stream.findFirst rejects a null element, so mapping first would throw a
        // bare NullPointerException when the setting is absent -- which is the
        // case this test exists to report clearly.
        return new YamlPropertySourceLoader().load(configuration.getDescription(), configuration).stream()
                .map(source -> source.getProperty("spring.jpa.hibernate.ddl-auto"))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Test
    void aCheckConstraintNoMappingCouldExpressIsEnforced() {
        // routes_fare_non_negative exists only in V2. A check clause cannot be
        // written as a mapping, so under Hibernate's generated schema it was not
        // merely differently named — it was absent, and a negative fare reached
        // the table unopposed.
        assertThatThrownBy(() -> jdbc.update("""
                insert into routes (id, origin, destination, fare, duration_minutes, status)
                values (?, 'AU', 'Asok', -1.00, 45, 'ACTIVE')
                """, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
