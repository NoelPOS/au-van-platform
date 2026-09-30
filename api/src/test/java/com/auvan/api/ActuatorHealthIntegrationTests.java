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

    @Test
    void theDatabaseIndicatorStillDecidesHealth() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }

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
        return new YamlPropertySourceLoader().load(configuration.getDescription(), configuration).stream()
                .map(source -> source.getProperty("management.health.redis.enabled"))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}
