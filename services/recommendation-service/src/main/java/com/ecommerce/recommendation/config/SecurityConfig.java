package com.ecommerce.recommendation.config;

import com.ecommerce.common.security.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@Profile({"staging", "prod"})
public class SecurityConfig extends BaseSecurityConfig {

    private static final String[] FULLY_PUBLIC = {
        "/api/v1/recommendations/trending",
        "/api/v1/recommendations/trending/**",
        "/api/v1/recommendations/product/**",
        "/api/v1/recommendations/session/**",
        "/actuator/health",
        "/actuator/health/**",
        "/actuator/prometheus"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(FULLY_PUBLIC).permitAll()
                .requestMatchers("/api/v1/recommendations/user/**").authenticated()
                .requestMatchers("/api/v1/recommendations/cart").authenticated()
                .requestMatchers("/api/v1/recommendations/feedback").authenticated()
                .requestMatchers("/internal/**").hasRole("INTERNAL_SERVICE")
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtConverter())))
            .build();
    }
}
