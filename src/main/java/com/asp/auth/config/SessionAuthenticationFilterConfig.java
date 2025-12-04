package com.asp.auth.config;

import com.asp.auth.security.SessionAuthenticationFilter;
import com.asp.auth.service.AuthService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration bean for SessionAuthenticationFilter.
 * Separated from DashboardSecurityConfig for clarity.
 */
@Configuration
public class SessionAuthenticationFilterConfig {

    @Bean
    public SessionAuthenticationFilter sessionAuthenticationFilter(AuthService authService) {
        return new SessionAuthenticationFilter(authService);
    }
}

