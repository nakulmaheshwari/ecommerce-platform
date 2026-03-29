package com.ecommerce.review.config;

import com.ecommerce.common.security.BaseSecurityConfig;
import com.ecommerce.common.security.KeycloakJwtConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig extends BaseSecurityConfig {

    // Review reads are fully public — anyone can read product reviews
    private static final String[] REVIEW_PUBLIC = {
        "/api/v1/products/*/reviews",
        "/api/v1/products/*/reviews/aggregate"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(c -> c.disable())
            .sessionManagement(s -> s
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .requestMatchers(REVIEW_PUBLIC).permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(
                    new KeycloakJwtConverter())))
            .build();
    }
}
