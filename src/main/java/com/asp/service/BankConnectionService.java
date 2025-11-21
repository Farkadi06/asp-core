package com.asp.service;

import com.asp.domain.Bank;
import com.asp.domain.BankConnection;
import com.asp.domain.BankConnectionRepository;
import com.asp.domain.BankRepository;
import com.asp.domain.Tenant;
import com.asp.domain.TenantRepository;
import com.asp.dto.BankConnectionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankConnectionService {

    private final BankConnectionRepository bankConnectionRepository;
    private final BankRepository bankRepository;
    private final TenantRepository tenantRepository;

    @Transactional
    public BankConnectionResponse createConnection(UUID bankId, UUID tenantId) {
        log.debug("Creating bank connection for bank {} and tenant {}", bankId, tenantId);

        // Validate tenant exists
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        // Validate bank exists and is active
        Bank bank = bankRepository.findById(bankId)
            .orElseThrow(() -> new IllegalArgumentException("Bank not found: " + bankId));

        if (bank.getStatus() != Bank.BankStatus.ACTIVE) {
            throw new IllegalStateException("Bank is not ACTIVE: " + bankId);
        }

        // Determine next step based on connection type
        String nextStep = determineNextStep(bank.getConnectionType());

        // Create connection
        BankConnection connection = BankConnection.builder()
            .tenant(tenant)
            .bank(bank)
            .status(BankConnection.ConnectionStatus.PENDING)
            .step(nextStep)
            .metadata(new HashMap<>())
            .build();

        BankConnection savedConnection = bankConnectionRepository.save(connection);

        log.info("Created bank connection {} for tenant {} with bank {}", 
            savedConnection.getId(), tenantId, bankId);

        return mapToResponse(savedConnection);
    }

    @Transactional(readOnly = true)
    public List<BankConnectionResponse> listConnections(UUID tenantId) {
        log.debug("Listing bank connections for tenant: {}", tenantId);
        List<BankConnection> connections = bankConnectionRepository.findAllByTenantId(tenantId);

        return connections.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BankConnectionResponse getConnection(UUID connectionId, UUID tenantId) {
        log.debug("Getting bank connection {} for tenant: {}", connectionId, tenantId);
        
        BankConnection connection = bankConnectionRepository.findByIdAndTenantId(connectionId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Bank connection not found: " + connectionId));

        return mapToResponse(connection);
    }

    private String determineNextStep(Bank.ConnectionType connectionType) {
        return switch (connectionType) {
            case OAUTH -> "oauth_redirect";
            case PDF -> "upload_pdf";
            case SCRAPER -> "credentials";
            case UNKNOWN -> "unsupported";
        };
    }

    private BankConnectionResponse mapToResponse(BankConnection connection) {
        return BankConnectionResponse.builder()
            .connectionId(connection.getId())
            .bankId(connection.getBank().getId())
            .status(connection.getStatus().name())
            .nextStep(connection.getStep())
            .metadata(connection.getMetadata() != null ? connection.getMetadata() : new HashMap<>())
            .build();
    }
}

