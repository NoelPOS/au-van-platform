package com.auvan.api.auth.bootstrap;

import com.auvan.api.ApiApplication;
import com.auvan.api.auth.service.AdminBootstrapService;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

public final class AdminBootstrapApplication {
    private AdminBootstrapApplication() { }

    public static void main(String[] args) {
        var context = new SpringApplicationBuilder(ApiApplication.class)
                .web(WebApplicationType.NONE)
                .run(args);
        try {
            context.getBean(AdminBootstrapService.class).bootstrap();
            System.out.println("Administrator bootstrap completed.");
        } finally {
            context.close();
        }
    }
}
