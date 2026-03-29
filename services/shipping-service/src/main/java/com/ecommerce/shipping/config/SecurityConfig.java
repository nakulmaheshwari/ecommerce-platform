package com.ecommerce.shipping.config;

import com.ecommerce.common.security.BaseSecurityConfig;
import com.ecommerce.common.security.KeycloakJwtConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig extends BaseSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                .requestMatchers("/api/v1/shipments/track/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j ->
                j.jwtAuthenticationConverter(new KeycloakJwtConverter())))
            .build();
    }
}
