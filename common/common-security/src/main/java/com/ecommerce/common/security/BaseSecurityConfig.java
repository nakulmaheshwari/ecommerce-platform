package com.ecommerce.common.security;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@EnableMethodSecurity
public abstract class BaseSecurityConfig {

    // Endpoints every service exposes publicly — health, metrics, docs
    protected static final String[] PUBLIC_ENDPOINTS = {
        "/actuator/health",
        "/actuator/health/**",
        "/actuator/info",
        "/v3/api-docs/**",
        "/swagger-ui/**"
    };

    protected SecurityFilterChain configure(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())           // Stateless — no CSRF needed
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(new KeycloakJwtConverter())))
            .build();
    }
}
