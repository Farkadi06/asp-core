package com.asp.service;

import com.asp.domain.BankConnection;
import com.asp.domain.BankConnectionRepository;
import com.asp.domain.Ingestion;
import com.asp.domain.IngestionRepository;
import com.asp.domain.IngestionSource;
import com.asp.domain.IngestionStatus;
import com.asp.domain.IngestionType;
import com.asp.dto.IngestionResponse;
import com.asp.storage.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final IngestionRepository ingestionRepository;
    private final BankConnectionRepository bankConnectionRepository;
    private final DocumentStorageService documentStorageService;

    @Transactional
    public IngestionResponse createIngestionForPdf(UUID tenantId, UUID bankConnectionId, MultipartFile file) {
        log.debug("Creating PDF ingestion for tenant {}, bankConnection {}, file: {}", 
            tenantId, bankConnectionId, file != null ? file.getOriginalFilename() : "null");

        // Validate file is not null and not empty (for debugging, we allow null but will fail later)
        // TODO: Remove this temporary debugging allowance and enforce file validation
        if (file == null || file.isEmpty()) {
            log.error("File validation failed - file is null or empty. Tenant: {}, BankConnection: {}", 
                tenantId, bankConnectionId);
            throw new IllegalArgumentException("File cannot be null or empty");
        }

        // Optional: validate content type (log warning if not PDF)
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("application/pdf")) {
            log.warn("File {} has content type {}, expected application/pdf", 
                file.getOriginalFilename(), contentType);
        }

        // Load BankConnection by bankConnectionId and tenantId
        BankConnection bankConnection = bankConnectionRepository.findByIdAndTenantId(bankConnectionId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Bank connection not found: " + bankConnectionId));

        // Read userRef from BankConnection
        String userRef = bankConnection.getUserRef();
        if (userRef == null || userRef.trim().isEmpty()) {
            throw new IllegalStateException("Bank connection has no userRef");
        }

        // Create Ingestion entity
        Ingestion ingestion = Ingestion.builder()
            .tenant(bankConnection.getTenant())
            .bankConnection(bankConnection)
            .userRef(userRef)
            .status(IngestionStatus.PENDING)
            .source(IngestionSource.MANUAL_UPLOAD)
            .ingestionType(IngestionType.PDF_STATEMENT)
            .originalFileName(file.getOriginalFilename())
            .fileSize(file.getSize())
            .build();

        // Save ingestion to obtain its id
        Ingestion savedIngestion = ingestionRepository.save(ingestion);

        try {
            // Store file using DocumentStorageService
            String storagePath = documentStorageService.storeIngestionFile(
                tenantId, 
                savedIngestion.getId(), 
                file.getOriginalFilename(), 
                file.getInputStream()
            );

            // Update ingestion with storage path
            savedIngestion.setStoragePath(storagePath);
            savedIngestion = ingestionRepository.save(savedIngestion);

            log.info("Created ingestion {} for tenant {}, bankConnection {}, userRef {}", 
                savedIngestion.getId(), tenantId, bankConnectionId, userRef);

        } catch (IOException e) {
            // If storage fails, mark ingestion as FAILED
            log.error("Failed to store file for ingestion {}", savedIngestion.getId(), e);
            savedIngestion.setStatus(IngestionStatus.FAILED);
            savedIngestion.setErrorCode("STORAGE_ERROR");
            savedIngestion.setErrorMessage("Failed to store file: " + e.getMessage());
            savedIngestion = ingestionRepository.save(savedIngestion);
        }

        return mapToResponse(savedIngestion);
    }

    @Transactional(readOnly = true)
    public IngestionResponse getIngestion(UUID tenantId, UUID ingestionId) {
        log.debug("Getting ingestion {} for tenant: {}", ingestionId, tenantId);
        
        Ingestion ingestion = ingestionRepository.findByIdAndTenantId(ingestionId, tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Ingestion not found: " + ingestionId));

        return mapToResponse(ingestion);
    }

    @Transactional(readOnly = true)
    public List<IngestionResponse> listIngestions(UUID tenantId, String userRef, UUID bankConnectionId) {
        log.debug("Listing ingestions for tenant: {}, userRef: {}, bankConnectionId: {}", 
            tenantId, userRef, bankConnectionId);

        List<Ingestion> ingestions;
        
        if (StringUtils.hasText(userRef)) {
            // Filter by tenantId + userRef
            ingestions = ingestionRepository.findAllByTenantIdAndUserRef(tenantId, userRef.trim());
        } else if (bankConnectionId != null) {
            // Filter by tenantId + bankConnectionId
            ingestions = ingestionRepository.findAllByTenantIdAndBankConnectionId(tenantId, bankConnectionId);
        } else {
            // List all for tenant
            ingestions = ingestionRepository.findAllByTenantId(tenantId);
        }

        return ingestions.stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }

    /**
     * Processes pending ingestions.
     * TODO: This will later:
     * - pick PENDING ingestions
     * - mark PROCESSING
     * - dispatch to parsing pipeline
     * For now, just a skeleton method.
     */
    @Transactional
    public void processPendingIngestions() {
        log.debug("Processing pending ingestions (skeleton - not yet implemented)");
        // TODO: Implement processing logic
    }

    private IngestionResponse mapToResponse(Ingestion ingestion) {
        return IngestionResponse.builder()
            .id(ingestion.getId())
            .bankConnectionId(ingestion.getBankConnection().getId())
            .userRef(ingestion.getUserRef())
            .status(ingestion.getStatus().name())
            .source(ingestion.getSource().name())
            .ingestionType(ingestion.getIngestionType().name())
            .originalFileName(ingestion.getOriginalFileName())
            .fileSize(ingestion.getFileSize())
            .storagePath(ingestion.getStoragePath())
            .errorCode(ingestion.getErrorCode())
            .errorMessage(ingestion.getErrorMessage())
            .createdAt(ingestion.getCreatedAt())
            .updatedAt(ingestion.getUpdatedAt())
            .processedAt(ingestion.getProcessedAt())
            .build();
    }
}

