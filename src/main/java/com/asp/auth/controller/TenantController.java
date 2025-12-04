package com.asp.auth.controller;

import com.asp.auth.dto.TenantMeResponse;
import com.asp.auth.security.UserPrincipal;
import com.asp.domain.Tenant;
import com.asp.domain.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller for tenant-related endpoints (internal/dashboard use).
 */
@RestController
@RequestMapping("/internal/tenant")
@RequiredArgsConstructor
@Slf4j
public class TenantController {

    private final TenantRepository tenantRepository;

    /**
     * Returns the current tenant information for the authenticated user.
     * Requires authenticated session (UserPrincipal).
     */
    @GetMapping("/me")
    public ResponseEntity<TenantMeResponse> getCurrentTenant() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof UserPrincipal)) {
            log.warn("Unauthenticated request to /internal/tenant/me");
            return ResponseEntity.status(401).build();
        }

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        UUID tenantId = principal.tenantId();

        log.debug("Getting tenant info for tenantId: {}", tenantId);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        TenantMeResponse response = TenantMeResponse.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .slug(tenant.getSlug())
                .status(tenant.getStatus().name())
                .plan(tenant.getPlan().name())
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();

        return ResponseEntity.ok(response);
    }
}

