package com.asp.service;

import com.asp.domain.BankConnection;
import com.asp.domain.BankConnectionRepository;
import com.asp.domain.Ingestion;
import com.asp.domain.IngestionRepository;
import com.asp.domain.IngestionSource;
import com.asp.domain.IngestionStatus;
import com.asp.domain.IngestionType;
import com.asp.dto.IngestionResponse;
import com.asp.service.storage.S3UploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
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
    private final SqsIngestionPublisher sqsIngestionPublisher;
    private final S3UploadService s3UploadService;

    @Transactional
    public IngestionResponse createIngestionForPdf(UUID tenantId, UUID bankConnectionId, MultipartFile file) {
        // Generate trace ID for request tracking
        String traceId = UUID.randomUUID().toString();
        
        // Set MDC context for logging
        MDC.put("tenant_id", tenantId != null ? tenantId.toString() : null);
        MDC.put("trace_id", traceId);
        
        try {
            log.debug("Creating PDF ingestion for tenant {}, bankConnection {}, file: {}, traceId: {}", 
                tenantId, bankConnectionId, file != null ? file.getOriginalFilename() : "null", traceId);

            // Validate file is not null and not empty (for debugging, we allow null but will fail later)
            // TODO: Remove this temporary debugging allowance and enforce file validation
            if (file == null || file.isEmpty()) {
                log.error("File validation failed - file is null or empty. Tenant: {}, BankConnection: {}, traceId: {}", 
                    tenantId, bankConnectionId, traceId);
                throw new IllegalArgumentException("File cannot be null or empty");
            }

            // Optional: validate content type (log warning if not PDF)
            String fileContentType = file.getContentType();
            if (fileContentType == null || !fileContentType.startsWith("application/pdf")) {
                log.warn("File {} has content type {}, expected application/pdf, traceId: {}", 
                    file.getOriginalFilename(), fileContentType, traceId);
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
            
            // Update MDC with ingestion ID
            MDC.put("ingestion_id", savedIngestion.getId().toString());

            // Build S3 storage path: tenants/{tenantId}/ingestions/{ingestionId}/original/{originalFileName}
            String sanitizedFileName = sanitizeFileName(file.getOriginalFilename());
            String storagePath = String.format("tenants/%s/ingestions/%s/original/%s",
                    tenantId.toString(),
                    savedIngestion.getId().toString(),
                    sanitizedFileName);

            try {
                // Upload file directly to S3
                String contentType = fileContentType != null && !fileContentType.isBlank() 
                    ? fileContentType 
                    : "application/pdf"; // Default for PDF files

                s3UploadService.uploadFile(storagePath, file.getInputStream(), file.getSize(), contentType);

                // Update ingestion with storage path
                savedIngestion.setStoragePath(storagePath);
                savedIngestion = ingestionRepository.save(savedIngestion);

                log.info("Created ingestion {} for tenant {}, bankConnection {}, userRef {}, traceId: {}, storagePath: {}", 
                    savedIngestion.getId(), tenantId, bankConnectionId, userRef, traceId, storagePath);

            } catch (Exception e) {
                // If S3 upload fails, mark ingestion as FAILED
                log.error("Failed to upload file to S3 for ingestion {}, traceId: {}", savedIngestion.getId(), traceId, e);
                savedIngestion.setStatus(IngestionStatus.FAILED);
                savedIngestion.setErrorCode("STORAGE_ERROR");
                savedIngestion.setErrorMessage("Failed to upload file to S3: " + e.getMessage());
                savedIngestion = ingestionRepository.save(savedIngestion);
                
                // Don't publish to SQS if storage failed
                return mapToResponse(savedIngestion);
            }

            // Publish to SQS queue after successful ingestion creation and file storage
            if (storagePath != null && savedIngestion.getStatus() == IngestionStatus.PENDING) {
                try {
                    String messageId = sqsIngestionPublisher.publishIngestionMessage(
                        savedIngestion.getId(),
                        tenantId,
                        storagePath,
                        traceId
                    );
                    log.info("Published ingestion {} to SQS queue - messageId: {}, traceId: {}", 
                        savedIngestion.getId(), messageId, traceId);
                } catch (RuntimeException e) {
                    // Log error but don't fail ingestion creation
                    // The ingestion is already saved and can be processed manually later
                    log.error("Failed to publish ingestion {} to SQS queue, traceId: {}. " +
                        "Ingestion created successfully but will need manual processing.", 
                        savedIngestion.getId(), traceId, e);
                    // Optionally, you could mark ingestion with a warning flag here
                }
            }

            return mapToResponse(savedIngestion);
            
        } finally {
            // Clean up MDC context
            MDC.remove("tenant_id");
            MDC.remove("ingestion_id");
            MDC.remove("trace_id");
        }
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

    /**
     * Sanitizes filename to prevent path traversal attacks and normalize characters.
     * 
     * @param fileName original filename
     * @return sanitized filename
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "unnamed.pdf";
        }
        
        // Remove path separators and parent directory references
        String sanitized = fileName.replaceAll("[/\\\\]", "_")
                                   .replaceAll("\\.\\.", "_")
                                   .replaceAll("[^a-zA-Z0-9._-]", "_")
                                   .trim();
        
        // Ensure it's not empty after sanitization
        if (sanitized.isEmpty()) {
            sanitized = "unnamed.pdf";
        }
        
        // Ensure it has an extension or add .pdf as default
        if (!sanitized.contains(".")) {
            sanitized += ".pdf";
        }
        
        return sanitized;
    }
}

