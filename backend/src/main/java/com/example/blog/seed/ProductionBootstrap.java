package com.example.blog.seed;

import com.example.blog.auth.service.UserService;
import com.example.blog.config.BootstrapProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@Order(0)
public class ProductionBootstrap implements ApplicationRunner {
    private final UserService userService;
    private final BootstrapProperties properties;

    public ProductionBootstrap(UserService userService, BootstrapProperties properties) {
        this.userService = userService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"admin".equalsIgnoreCase(properties.getUsername().trim())) {
            userService.disableIfPresent("admin");
        }
        userService.configureBootstrap(properties.getUsername(), properties.getPassword(),
                properties.getDisplayName(), properties.getRole());
    }
}
