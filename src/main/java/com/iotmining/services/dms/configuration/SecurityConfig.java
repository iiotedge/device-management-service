package com.iotmining.services.dms.configuration;

import com.iotmining.services.dms.security.LiteJwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // Required for the @PreAuthorize checks on every controller
@RequiredArgsConstructor
public class SecurityConfig {

    private final LiteJwtAuthenticationFilter liteJwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            ObjectProvider<CorsConfigurationSource> corsConfigurationSource) throws Exception {

        // A consuming profile opts into Spring-managed CORS by defining its own
        // CorsConfigurationSource bean (see CorsConfig, gated behind @Profile("dev")).
        // Without one - the default in prod, where Nginx/the gateway already adds
        // CORS headers for this service's routes - CORS stays disabled here to
        // avoid duplicate Access-Control-Allow-Origin headers.
        CorsConfigurationSource corsSource = corsConfigurationSource.getIfAvailable();
        if (corsSource != null) {
            http.cors(cors -> cors.configurationSource(corsSource));
        } else {
            http.cors(AbstractHttpConfigurer::disable);
        }

        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(liteJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
