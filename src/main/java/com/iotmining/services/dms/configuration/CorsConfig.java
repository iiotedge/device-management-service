package com.iotmining.services.dms.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@Configuration
public class CorsConfig {

    // @Primary is required: Spring MVC's auto-registered mvcHandlerMappingIntrospector
    // bean also implements CorsConfigurationSource, so without this, SecurityConfig's
    // ObjectProvider<CorsConfigurationSource> finds two candidates and throws
    // NoUniqueBeanDefinitionException instead of picking one.
    @Bean
    @Primary
    @Profile("dev")
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Match your React dev server port
        config.setAllowedOrigins(Collections.singletonList("http://localhost:3000"));

        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Cache-Control",
                "Content-Type",
                "X-Auth-Token",
                "X-Tenant-ID",
                "X-User-ID"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // Cache preflight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}