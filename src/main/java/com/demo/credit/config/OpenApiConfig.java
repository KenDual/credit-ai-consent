package com.demo.credit.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    public OpenAPI creditOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Credit AI Consent API")
                .description("MVP endpoints: Applications, Scores, Consents")
                .version("v1"));
    }
}
