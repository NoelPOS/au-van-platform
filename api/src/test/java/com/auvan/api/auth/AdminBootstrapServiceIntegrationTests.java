package com.auvan.api.auth;

import com.auvan.api.AuthenticationTestSupport;
import com.auvan.api.auth.config.AuthProperties;
import com.auvan.api.auth.entity.AppUser;
import com.auvan.api.auth.entity.ApplicationRole;
import com.auvan.api.auth.repository.AppUserRepository;
import com.auvan.api.auth.service.AdminBootstrapService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "auth.admin-bootstrap.line-subject=Utrusted-admin")
class AdminBootstrapServiceIntegrationTests extends AuthenticationTestSupport {
    @Autowired
    private AdminBootstrapService adminBootstrapService;

    @Autowired
    private AppUserRepository users;

    @BeforeEach
    void clearUsers() {
        users.deleteAll();
    }

    @Test
    void createsTheConfiguredUserAsAnAdministrator() {
        adminBootstrapService.bootstrap();

        var user = users.findByLineSubject("Utrusted-admin").orElseThrow();
        assertThat(user.getRole()).isEqualTo(ApplicationRole.ADMIN);
    }

    @Test
    void promotesAnExistingStudentAndIsSafeToRunAgain() {
        users.save(new AppUser("Utrusted-admin", "Noel"));

        adminBootstrapService.bootstrap();
        adminBootstrapService.bootstrap();

        var user = users.findByLineSubject("Utrusted-admin").orElseThrow();
        assertThat(user.getRole()).isEqualTo(ApplicationRole.ADMIN);
        assertThat(users.count()).isOne();
    }

    @Test
    void rejectsMissingConfigurationWithoutChangingUsers() {
        var missingConfiguration = new AuthProperties(null, null, new AuthProperties.AdminBootstrap("  "));
        var service = new AdminBootstrapService(users, missingConfiguration);

        assertThatThrownBy(service::bootstrap)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_BOOTSTRAP_LINE_SUBJECT");
        assertThat(users.count()).isZero();
    }
}
