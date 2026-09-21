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

        assertThat(applied).containsExactly("1", "2", "3");
    }

    @Test
    void theSchemaCarriesATableNoEntityMaps() {
        // idempotency_keys exists only in V3 and no entity maps it, so Hibernate
        // could never have generated it. Being able to query it at all is the
        // proof that the migrations, not the mappings, built this schema.
        assertThat(jdbc.queryForObject("select count(*) from idempotency_keys", Integer.class)).isZero();
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
        return new YamlPropertySourceLoader().load(configuration.getDescription(), configuration).stream()
                .map(source -> source.getProperty("spring.jpa.hibernate.ddl-auto"))
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
