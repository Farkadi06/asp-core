package com.asp.api;

import com.asp.dto.IngestionResponse;
import com.asp.service.IngestionService;
import com.asp.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/ingestions")
@RequiredArgsConstructor
@Slf4j
public class IngestionController {

    private final IngestionService ingestionService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<IngestionResponse> createIngestion(
            HttpServletRequest request,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @RequestParam("bankConnectionId") UUID bankConnectionId) {
        
        // Log request details for debugging
        String contentType = request.getContentType();
        log.info("Received ingestion request - Content-Type: {}, Content-Length: {}", 
            contentType, request.getContentLength());
        
        if (file != null) {
            log.info("File received - name: {}, size: {}, contentType: {}, isEmpty: {}", 
                file.getOriginalFilename(), 
                file.getSize(), 
                file.getContentType(),
                file.isEmpty());
        } else {
            log.warn("No file received in request (file is null)");
        }
        
        UUID tenantId = TenantContext.getTenantId();

        if (tenantId == null) {
            log.warn("Tenant context not set for ingestion creation");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.debug("Creating ingestion for tenant {}, bankConnection {}, file: {}", 
            tenantId, bankConnectionId, file != null ? file.getOriginalFilename() : "null");

        IngestionResponse response = ingestionService.createIngestionForPdf(tenantId, bankConnectionId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<IngestionResponse> getIngestion(@PathVariable UUID id) {
        UUID tenantId = TenantContext.getTenantId();

        if (tenantId == null) {
            log.warn("Tenant context not set for getting ingestion");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.debug("Getting ingestion {} for tenant: {}", id, tenantId);
        IngestionResponse response = ingestionService.getIngestion(tenantId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<IngestionResponse>> listIngestions(
            @RequestParam(required = false) String userRef,
            @RequestParam(required = false) UUID bankConnectionId) {
        
        UUID tenantId = TenantContext.getTenantId();

        if (tenantId == null) {
            log.warn("Tenant context not set for listing ingestions");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.debug("Listing ingestions for tenant: {}, userRef: {}, bankConnectionId: {}", 
            tenantId, userRef, bankConnectionId);

        List<IngestionResponse> responses = ingestionService.listIngestions(tenantId, userRef, bankConnectionId);
        return ResponseEntity.ok(responses);
    }
}

