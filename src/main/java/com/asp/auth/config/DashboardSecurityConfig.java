package com.asp.auth.config;

import com.asp.auth.security.SessionAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for dashboard routes (/dashboard/**, /auth/**).
 * 
 * IMPORTANT: This configuration applies ONLY to dashboard/internal/auth routes.
 * It does NOT modify or replace the existing API Key authentication for /v1/** routes.
 * 
 * The existing SecurityConfig handles /v1/** API key authentication.
 * This config handles dashboard session-based authentication.
 */
@Configuration
@EnableWebSecurity
public class DashboardSecurityConfig {

    @Bean
    public SecurityFilterChain dashboardSecurityFilterChain(
            HttpSecurity http,
            SessionAuthenticationFilter sessionAuthenticationFilter) throws Exception {
        http
            .securityMatcher("/dashboard/**", "/auth/**", "/internal/**")
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .addFilterBefore(sessionAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/logout").permitAll()  // Logout endpoint (public, only destroys sessions)
                .requestMatchers("/auth/google/**").permitAll()  // Google OAuth endpoints
                .requestMatchers("/auth/session/me").permitAll()  // Session check endpoint
                .requestMatchers("/auth/**").permitAll()  // All other auth endpoints (dev login, etc.)
                .requestMatchers("/internal/ingestions/**").permitAll()  // Worker endpoints use X-Internal-Token (validated in controller)
                .requestMatchers("/internal/**").authenticated()  // Other internal endpoints require authenticated session (UserPrincipal)
                .anyRequest().authenticated()  // Dashboard pages require session
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allow frontend origin
        String frontendUrl = System.getenv().getOrDefault("FRONTEND_URL", "http://localhost:7000");
        configuration.setAllowedOrigins(List.of(frontendUrl));
        
        // Allow credentials (cookies)
        configuration.setAllowCredentials(true);
        
        // Allow all methods
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        
        // Allow all headers
        configuration.setAllowedHeaders(Arrays.asList("*"));
        
        // Expose headers that frontend might need
        configuration.setExposedHeaders(Arrays.asList("Set-Cookie"));
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        return source;
    }
}

