package com.asp.config;

import com.asp.security.ApiKeyAuthenticationFilter;
import com.asp.security.ApiKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(ApiKeyService apiKeyService, ObjectMapper objectMapper) {
        return new ApiKeyAuthenticationFilter(apiKeyService, objectMapper);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) throws Exception {
        // This security filter chain applies ONLY to /v1/** API routes
        // Dashboard routes (/dashboard/**, /auth/**) are handled by DashboardSecurityConfig
        http
            .securityMatcher("/v1/**", "/actuator/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/v1/ping").permitAll()  // Allow testing without auth
                .requestMatchers("/v1/api-keys", "/v1/api-keys/**").authenticated()  // API key management requires auth
                .requestMatchers("/v1/banks/**").authenticated()  // Bank directory requires auth
                .requestMatchers("/v1/bank-connections/**").authenticated()  // Bank connections require auth
                .requestMatchers("/v1/ingestions/**").authenticated()  // Ingestion requires auth
                .requestMatchers("/v1/accounts/**").authenticated()  // Account queries require auth
                .requestMatchers("/v1/**").authenticated()
                .anyRequest().permitAll())
            .addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

