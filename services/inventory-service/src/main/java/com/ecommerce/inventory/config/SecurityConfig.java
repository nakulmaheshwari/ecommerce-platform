package com.ecommerce.inventory.config;

import com.ecommerce.common.security.BaseSecurityConfig;
import com.ecommerce.common.security.KeycloakJwtConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the Inventory Service.
 * 
 * Defines which endpoints are public (product stock display)
 * and which require administrative privileges (adding stock, viewing history).
 * 
 * INTEGRATION:
 * Extends {@link BaseSecurityConfig} for common JWT and CORS settings.
 * Uses {@link KeycloakJwtConverter} to map Keycloak roles to Spring Security
 * authorities.
 */
@Configuration
public class SecurityConfig extends BaseSecurityConfig {

    /**
     * Configures the HTTP security filter chain.
     * 
     * PERMISSION MODEL:
     * - /availability, /{skuId}: PUBLIC (needed for product pages & checkouts).
     * - /add-stock, /movements, /low-stock: ADMIN (warehouse staff only).
     * - /actuator/**: PUBLIC (monitoring).
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers("/api/v1/inventory/*/availability").permitAll()
                        .requestMatchers("/api/v1/inventory/*").permitAll()
                        .requestMatchers("/api/v1/inventory/*/add-stock").hasRole("ADMIN")
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakJwtConverter())))
                .build();
    }
}
