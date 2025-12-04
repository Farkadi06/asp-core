package com.asp.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configuration properties for Google OAuth 2.0.
 */
@Configuration
@ConfigurationProperties(prefix = "google.oauth")
@Data
public class GoogleOAuthProperties {

    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private List<String> scopes = List.of("openid", "email", "profile");
}

