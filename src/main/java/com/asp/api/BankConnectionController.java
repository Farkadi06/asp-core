package com.asp.api;

import com.asp.dto.BankConnectionResponse;
import com.asp.dto.CreateBankConnectionRequest;
import com.asp.service.BankConnectionService;
import com.asp.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/bank-connections")
@RequiredArgsConstructor
@Slf4j
public class BankConnectionController {

    private final BankConnectionService bankConnectionService;

    @PostMapping("/{bankId}/connect")
    public ResponseEntity<BankConnectionResponse> createConnection(
            @PathVariable UUID bankId,
            @Valid @RequestBody CreateBankConnectionRequest request) {
        UUID tenantId = TenantContext.getTenantId();

        if (tenantId == null) {
            log.warn("Tenant context not set for bank connection creation");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.debug("Creating bank connection for bank {}, tenant {}, userRef {}", 
            bankId, tenantId, request.getUserRef());
        BankConnectionResponse response = bankConnectionService.createConnection(
            bankId, tenantId, request.getUserRef());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<BankConnectionResponse>> listConnections(
            @RequestParam(required = false) String userRef) {
        UUID tenantId = TenantContext.getTenantId();

        if (tenantId == null) {
            log.warn("Tenant context not set for listing bank connections");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.debug("Listing bank connections for tenant: {}, userRef: {}", tenantId, userRef);
        List<BankConnectionResponse> connections = bankConnectionService.listConnections(tenantId, userRef);
        return ResponseEntity.ok(connections);
    }

    @GetMapping("/{connectionId}")
    public ResponseEntity<BankConnectionResponse> getConnection(@PathVariable UUID connectionId) {
        UUID tenantId = TenantContext.getTenantId();

        if (tenantId == null) {
            log.warn("Tenant context not set for getting bank connection");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.debug("Getting bank connection {} for tenant: {}", connectionId, tenantId);
        BankConnectionResponse connection = bankConnectionService.getConnection(connectionId, tenantId);
        return ResponseEntity.ok(connection);
    }
}

