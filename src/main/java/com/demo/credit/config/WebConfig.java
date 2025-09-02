package com.demo.credit.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origins:}")
    private String corsOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var reg = registry.addMapping("/**")
                .allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS")
                .allowedHeaders("*");
        if (StringUtils.hasText(corsOrigins)) {
            String[] origins = Arrays.stream(corsOrigins.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).toArray(String[]::new);
            reg.allowedOrigins(origins).allowCredentials(true);
        }
    }
}
