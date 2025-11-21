package com.asp.security;

import com.asp.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Api-Key";
    private final ApiKeyService apiKeyService;
    private final ObjectMapper objectMapper;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService, ObjectMapper objectMapper) {
        this.apiKeyService = apiKeyService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKeyHeader = request.getHeader(API_KEY_HEADER);

        if (apiKeyHeader == null || apiKeyHeader.trim().isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Parse API key token
            ApiKeyToken token;
            try {
                token = ApiKeyToken.parse(apiKeyHeader);
            } catch (IllegalArgumentException e) {
                log.debug("Failed to parse API key: {}", e.getMessage());
                sendErrorResponse(response, "invalid_api_key");
                return;
            }

            // Validate API key
            var validationResult = apiKeyService.validateApiKey(token.getPrefix(), token.getSecret());

            if (validationResult.isEmpty()) {
                log.debug("API key validation failed for prefix: {}", token.getPrefix());
                sendErrorResponse(response, "invalid_api_key");
                return;
            }

            UUID tenantId = validationResult.get().tenantId();

            // Set TenantContext (ThreadLocal)
            TenantContext.setTenantId(tenantId);

            // Store tenantId in request attribute for use in transaction synchronization
            request.setAttribute("asp.tenant_id", tenantId);

            // Create fully authenticated Authentication object
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                tenantId.toString(),  // principal: tenantId as string
                null,                 // credentials: null
                List.of(new SimpleGrantedAuthority("ROLE_API_KEY"))  // authorities: ROLE_API_KEY
            );

            // Set authentication in SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated tenant {} with API key", tenantId);

            // Continue filter chain
            try {
                filterChain.doFilter(request, response);
            } finally {
                // Always clear TenantContext and SecurityContext after request
                TenantContext.clear();
                SecurityContextHolder.clearContext();
            }

        } catch (Exception e) {
            log.error("Error processing API key authentication", e);
            TenantContext.clear();
            SecurityContextHolder.clearContext();
            sendErrorResponse(response, "invalid_api_key");
        }
    }

    private void sendErrorResponse(HttpServletResponse response, String error) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", error);

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}

