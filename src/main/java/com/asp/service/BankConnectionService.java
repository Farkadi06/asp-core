package com.asp.service;

import com.asp.domain.Bank;
import com.asp.domain.BankConnection;
import com.asp.domain.BankConnectionNextStepType;
import com.asp.domain.BankConnectionRepository;
import com.asp.domain.BankRepository;
import com.asp.domain.Tenant;
import com.asp.domain.TenantRepository;
import com.asp.dto.BankConnectionNextStep;
import com.asp.dto.BankConnectionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankConnectionService {

    private final BankConnectionRepository bankConnectionRepository;
    private final BankRepository bankRepository;
    private final TenantRepository tenantRepository;

    @Value("${asp.platform.public-base-url:http://localhost:8080}")
    private String publicBaseUrl;

    @Transactional
    public BankConnectionResponse createConnection(UUID bankId, UUID tenantId, String userRef) {
        log.debug("Creating bank connection for bank {}, tenant {}, userRef {}", bankId, tenantId, userRef);

        // Validate tenant exists
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        // Validate bank exists and is active
        Bank bank = bankRepository.findById(bankId)
            .orElseThrow(() -> new IllegalArgumentException("Bank not found: " + bankId));

        if (bank.getStatus() != Bank.BankStatus.ACTIVE) {
            throw new IllegalStateException("Bank is not ACTIVE: " + bankId);
        }

        // Validate userRef is not null or blank (should be validated by DTO, but double-check)
        if (userRef == null || userRef.trim().isEmpty()) {
            throw new IllegalArgumentException("userRef cannot be null or empty");
        }

        // Determine connection status based on bank connection type
        BankConnection.ConnectionStatus connectionStatus = determineConnectionStatus(bank.getConnectionType());

        // Create connection
        BankConnection connection = BankConnection.builder()
            .tenant(tenant)
            .bank(bank)
            .status(connectionStatus)
            .step(null)  // No longer storing step string, computed on-the-fly
            .userRef(userRef.trim())
            .metadata(new HashMap<>())
            .build();

        BankConnection savedConnection = bankConnectionRepository.save(connection);

        log.info("Created bank connection {} for tenant {}, bank {}, userRef {}", 
            savedConnection.getId(), tenantId, bankId, userRef);

        // Compute next step and return response
        return mapToResponse(savedConnection, bank);
    }

    @Transactional(readOnly = true)
    public List<BankConnectionResponse> listConnections(UUID tenantId, String userRef) {
        log.debug("Listing bank connections for tenant: {}, userRef: {}", tenantId, userRef);
        
        List<BankConnection> connections;
        if (userRef != null && !userRef.trim().isEmpty()) {
            // Filter by userRef if provided
            connections = bankConnectionRepository.findAllByTenantIdAndUserRef(tenantId, userRef.trim());
        } else {
            // Return all connections for tenant if no userRef filter
            connections = bankConnectionRepository.findAllByTenantId(tenantId);
        }

        return connections.stream()
            .map(connection -> {
                // Ensure bank is loaded (lazy loading)
                Bank bank = connection.getBank();
                return mapToResponse(connection, bank);
            })
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BankConnectionResponse getConnection(UUID connectionId, UUID tenantId) {
        log.debug("Getting bank connection {} for tenant: {}", connectionId, tenantId);
        
        BankConnection connection = bankConnectionRepository.findByIdAndTenantId(connectionId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Bank connection not found: " + connectionId));

        // Ensure bank is loaded (lazy loading)
        Bank bank = connection.getBank();
        return mapToResponse(connection, bank);
    }

    private BankConnection.ConnectionStatus determineConnectionStatus(Bank.ConnectionType connectionType) {
        return switch (connectionType) {
            case OAUTH -> BankConnection.ConnectionStatus.WAITING_OAUTH;
            case PDF -> BankConnection.ConnectionStatus.WAITING_UPLOAD;
            case SCRAPER -> BankConnection.ConnectionStatus.PENDING;  // Will wait for credentials
            case UNKNOWN -> BankConnection.ConnectionStatus.PENDING;
        };
    }

    /**
     * Determines the next step for a bank connection based on bank connection type and current state.
     * This method is pure and easily testable.
     */
    private BankConnectionNextStep determineNextStep(BankConnection connection, Bank bank) {
        BankConnectionNextStepType stepType;
        Map<String, Object> details = new HashMap<>();
        UUID connectionId = connection.getId();

        switch (bank.getConnectionType()) {
            case OAUTH -> {
                stepType = BankConnectionNextStepType.OAUTH_REDIRECT;
                String redirectUrl = publicBaseUrl + "/v1/bank-connections/" + connectionId + "/oauth/start";
                details.put("redirectUrl", redirectUrl);
            }
            case PDF -> {
                stepType = BankConnectionNextStepType.UPLOAD_PDF;
                String uploadUrl = "/v1/bank-connections/" + connectionId + "/documents";
                details.put("uploadUrl", uploadUrl);
            }
            case SCRAPER -> {
                stepType = BankConnectionNextStepType.CREDENTIALS_INPUT;
                details.put("fields", Arrays.asList("username", "password", "otp"));
            }
            default -> {
                stepType = BankConnectionNextStepType.UNSUPPORTED;
                details.put("reason", "CONNECTION_TYPE_NOT_SUPPORTED");
            }
        }

        return BankConnectionNextStep.builder()
            .type(stepType)
            .details(details)
            .build();
    }

    private BankConnectionResponse mapToResponse(BankConnection connection, Bank bank) {
        BankConnectionNextStep nextStep = determineNextStep(connection, bank);

        return BankConnectionResponse.builder()
            .connectionId(connection.getId())
            .bankId(bank.getId())
            .status(connection.getStatus().name())
            .userRef(connection.getUserRef())
            .nextStep(nextStep)
            .metadata(connection.getMetadata() != null ? connection.getMetadata() : new HashMap<>())
            .build();
    }
}

