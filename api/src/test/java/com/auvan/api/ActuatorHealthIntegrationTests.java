package com.auvan.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #31 makes {@code /actuator/health} load-bearing: it is the container
 * HEALTHCHECK and, in ADR-007's topology, the load balancer's target check.
 * {@code spring-boot-starter-data-redis} is on the classpath while no code
 * uses Redis, so Spring's auto-configured indicator reported the application
 * DOWN wherever Redis was absent — which is that topology, where ElastiCache
 * defaults off. Every task would have failed its check and it would have
 * looked like a networking fault.
 *
 * <p>The fixture needs no setup: {@code application.yml} in the test resources
 * already points Redis at port 0, so Redis is unreachable here exactly as it
 * is there.
 */
@SpringBootTest(properties = "management.endpoint.health.show-details=always")
@AutoConfigureMockMvc
class ActuatorHealthIntegrationTests extends AuthenticationTestSupport {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsUpWhileRedisIsUnreachable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.redis").doesNotExist());
    }

    /**
     * Disabling one indicator must not be read as disabling health reporting.
     * PostgreSQL is the authority for every booking, so an instance that
     * cannot reach it has to fail its check rather than keep taking traffic.
     */
    @Test
    void theDatabaseIndicatorStillDecidesHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

    /**
     * The test configuration replaces the main one outright rather than
     * merging with it, so a fix applied only here would leave the deployed
     * application reporting DOWN with nothing red to show for it. Both files
     * are asserted for that reason.
     */
    @Test
    void everyConfigurationDisablesTheRedisHealthIndicator() throws IOException {
        Resource[] configurations = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:/application.yml");

        assertThat(configurations).hasSize(2);
        for (Resource configuration : configurations) {
            assertThat(redisHealthEnabledIn(configuration)).as("%s", configuration).isEqualTo(false);
        }
    }

    private Object redisHealthEnabledIn(Resource configuration) throws IOException {
        // Stream.findFirst rejects a null element, so mapping first would throw
        // a bare NullPointerException when the setting is absent -- which is
        // the case this test exists to report clearly.
        return new YamlPropertySourceLoader().load(configuration.getDescription(), configuration).stream()
                .map(source -> source.getProperty("management.health.redis.enabled"))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
