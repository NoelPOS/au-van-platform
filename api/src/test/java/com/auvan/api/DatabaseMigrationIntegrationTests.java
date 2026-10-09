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

@SpringBootTest
class DatabaseMigrationIntegrationTests extends AuthenticationTestSupport {
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayRecordsEveryMigrationAsApplied() {
        List<String> applied = jdbc.queryForList("""
                select "version" from "flyway_schema_history"
                where "success" and "version" is not null
                order by "installed_rank"
                """, String.class);

        assertThat(applied).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14");
    }

    @Test
    void theSchemaCarriesABookingCheckConstraintNoMappingCouldExpress() {
        // Read from the catalog, not provoked: a foreign-key violation would look the same here
        // and the test would pass without the constraint.
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.table_constraints
                where upper(constraint_name) = 'BOOKINGS_TOTAL_FARE_NON_NEGATIVE'
                  and constraint_type = 'CHECK'
                """, Integer.class)).isOne();
    }

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
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Test
    void aCheckConstraintNoMappingCouldExpressIsEnforced() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into routes (id, origin, destination, fare, duration_minutes, status)
                values (?, 'AU', 'Asok', -1.00, 45, 'ACTIVE')
                """, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
