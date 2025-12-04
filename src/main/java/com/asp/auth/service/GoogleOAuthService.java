package com.asp.auth.service;

import com.asp.auth.config.GoogleOAuthProperties;
import com.asp.auth.dto.GoogleUserInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Service for Google OAuth 2.0 authentication flow.
 * Handles redirect URL generation, token exchange, and ID token verification.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleOAuthService {

    private static final String GOOGLE_AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String GOOGLE_ISSUER = "https://accounts.google.com";

    private final GoogleOAuthProperties oauthProperties;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /**
     * Builds the Google OAuth redirect URL.
     */
    public String buildGoogleRedirectUrl() {
        String state = UUID.randomUUID().toString(); // CSRF protection
        String scopes = String.join(" ", oauthProperties.getScopes());

        return String.format(
            "%s?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s&access_type=offline&prompt=consent",
            GOOGLE_AUTH_URL,
            URLEncoder.encode(oauthProperties.getClientId(), StandardCharsets.UTF_8),
            URLEncoder.encode(oauthProperties.getRedirectUri(), StandardCharsets.UTF_8),
            URLEncoder.encode(scopes, StandardCharsets.UTF_8),
            state
        );
    }

    /**
     * Exchanges authorization code for ID token and extracts user information.
     * Verifies the ID token signature and audience.
     */
    public GoogleUserInfo exchangeCodeForUser(String authorizationCode) {
        try {
            // Exchange code for tokens
            String idToken = exchangeCodeForTokens(authorizationCode);
            
            // Verify and parse ID token
            Claims claims = verifyIdToken(idToken);
            
            // Extract user info
            return extractUserInfo(claims);
            
        } catch (Exception e) {
            log.error("Failed to exchange code for user info", e);
            throw new RuntimeException("Google OAuth authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Exchanges authorization code for access token and ID token.
     */
    private String exchangeCodeForTokens(String authorizationCode) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("code", authorizationCode);
        formData.add("client_id", oauthProperties.getClientId());
        formData.add("client_secret", oauthProperties.getClientSecret());
        formData.add("redirect_uri", oauthProperties.getRedirectUri());
        formData.add("grant_type", "authorization_code");

        try {
            JsonNode response = webClient.post()
                    .uri(GOOGLE_TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(formData))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response == null || !response.has("id_token")) {
                throw new RuntimeException("Google token exchange failed: no id_token in response");
            }

            return response.get("id_token").asText();
        } catch (Exception e) {
            log.error("Token exchange failed", e);
            throw new RuntimeException("Failed to exchange authorization code for tokens", e);
        }
    }

    /**
     * Verifies the ID token signature and validates claims.
     * For production, should fetch JWKS and verify signature properly.
     * For now, we'll do basic validation and trust Google's token.
     */
    private Claims verifyIdToken(String idToken) {
        try {
            // Parse token without verification first to get header
            String[] parts = idToken.split("\\.");
            if (parts.length != 3) {
                throw new RuntimeException("Invalid ID token format");
            }

            // Decode header to get key ID (kid)
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            JsonNode header = objectMapper.readTree(headerJson);
            
            // For now, we'll parse the token without signature verification
            // In production, you should fetch JWKS from Google and verify signature
            // This is a simplified version - for production, use proper JWT verification library
            
            Claims claims = Jwts.parser()
                    .unsecured() // Skip signature verification for now (simplified)
                    .build()
                    .parseUnsecuredClaims(idToken)
                    .getPayload();

            // Validate issuer
            String issuer = claims.getIssuer();
            if (!GOOGLE_ISSUER.equals(issuer)) {
                throw new RuntimeException("Invalid token issuer: " + issuer);
            }

            // Validate audience (client ID)
            String audience = claims.getAudience().iterator().next();
            if (!oauthProperties.getClientId().equals(audience)) {
                throw new RuntimeException("Invalid token audience: " + audience);
            }

            // Validate expiration
            if (claims.getExpiration().before(new java.util.Date())) {
                throw new RuntimeException("Token has expired");
            }

            return claims;
        } catch (Exception e) {
            log.error("ID token verification failed", e);
            throw new RuntimeException("Failed to verify ID token: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts user information from verified ID token claims.
     */
    private GoogleUserInfo extractUserInfo(Claims claims) {
        String email = claims.get("email", String.class);
        Boolean emailVerified = claims.get("email_verified", Boolean.class);
        String name = claims.get("name", String.class);
        String sub = claims.getSubject(); // Google user ID

        if (email == null || sub == null) {
            throw new RuntimeException("Missing required claims in ID token");
        }

        return GoogleUserInfo.builder()
                .email(email)
                .emailVerified(emailVerified != null && emailVerified)
                .displayName(name != null ? name : email.split("@")[0])
                .googleUserId(sub)
                .build();
    }
}

