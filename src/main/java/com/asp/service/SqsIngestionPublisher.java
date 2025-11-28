package com.asp.service;

import com.asp.shared.dto.IngestionQueueMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for publishing ingestion messages to SQS queue.
 */
@Service
@Slf4j
public class SqsIngestionPublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final String queueUrl;

    public SqsIngestionPublisher(SqsClient sqsClient, 
                                  ObjectMapper objectMapper, 
                                  @Qualifier("sqsIngestionQueueUrl") String queueUrl) {
        this.sqsClient = sqsClient;
        this.objectMapper = objectMapper;
        this.queueUrl = queueUrl;
        if (queueUrl == null || queueUrl.trim().isEmpty()) {
            log.warn("SQS queue URL is not configured. SQS publishing will be disabled. " +
                "Set ASP_SQS_INGESTION_QUEUE_URL environment variable or configure aws.secrets-manager.secret-name to enable.");
        } else {
            log.info("SqsIngestionPublisher initialized with queue URL: {}", maskUrl(queueUrl));
        }
    }

    /**
     * Publishes an ingestion message to the SQS queue.
     * Synchronously waits for the message to be sent before returning.
     *
     * @param ingestionId the ingestion ID
     * @param tenantId the tenant ID
     * @param filePath the storage path of the uploaded file
     * @param traceId the trace ID for request tracking
     * @return the SQS message ID
     * @throws RuntimeException if SQS publish fails
     */
    public String publishIngestionMessage(UUID ingestionId, UUID tenantId, String filePath, String traceId) {
        // Check if queue URL is configured
        if (queueUrl == null || queueUrl.trim().isEmpty()) {
            log.warn("[SQS] Queue URL not configured. Skipping SQS publishing - ingestionId: {}, tenantId: {}, traceId: {}",
                ingestionId, tenantId, traceId);
            return null;
        }

        // Set MDC context for logging
        MDC.put("tenant_id", tenantId != null ? tenantId.toString() : null);
        MDC.put("ingestion_id", ingestionId != null ? ingestionId.toString() : null);
        MDC.put("trace_id", traceId);

        try {
            log.info("[SQS] Publishing ingestion message - ingestionId: {}, tenantId: {}, filePath: {}, traceId: {}",
                ingestionId, tenantId, maskPath(filePath), traceId);

            // Build queue message
            IngestionQueueMessage message = IngestionQueueMessage.builder()
                .version("1")
                .ingestionId(ingestionId)
                .tenantId(tenantId)
                .filePath(filePath)
                .createdAt(Instant.now())
                .traceId(traceId)
                .attempt(1)
                .build();

            // Serialize to JSON
            String messageBody;
            try {
                messageBody = objectMapper.writeValueAsString(message);
                log.debug("[SQS] Message payload: {}", messageBody);
            } catch (Exception e) {
                log.error("[SQS] Failed to serialize message to JSON", e);
                throw new RuntimeException("Failed to serialize ingestion message to JSON", e);
            }

            // Send message to SQS
            SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build();

            SendMessageResponse response;
            try {
                response = sqsClient.sendMessage(request);
                String messageId = response.messageId();

                log.info("[SQS] Successfully published ingestion message - ingestionId: {}, tenantId: {}, " +
                    "traceId: {}, messageId: {}", ingestionId, tenantId, traceId, messageId);

                // Log structured JSON output
                log.info("{\"event\":\"sqs_publish\",\"ingestionId\":\"{}\",\"tenantId\":\"{}\",\"traceId\":\"{}\"," +
                    "\"messageId\":\"{}\",\"status\":\"success\"}", ingestionId, tenantId, traceId, messageId);

                return messageId;

            } catch (SqsException e) {
                log.error("[SQS] SQS error publishing message - ingestionId: {}, tenantId: {}, traceId: {}, " +
                    "errorCode: {}, errorMessage: {}", ingestionId, tenantId, traceId, e.awsErrorDetails().errorCode(),
                    e.awsErrorDetails().errorMessage(), e);
                throw new RuntimeException("SQS publish failed: " + e.awsErrorDetails().errorMessage(), e);
            } catch (Exception e) {
                log.error("[SQS] Unexpected error publishing message - ingestionId: {}, tenantId: {}, traceId: {}",
                    ingestionId, tenantId, traceId, e);
                throw new RuntimeException("SQS publish failed", e);
            }

        } finally {
            // Clean up MDC context
            MDC.remove("tenant_id");
            MDC.remove("ingestion_id");
            MDC.remove("trace_id");
        }
    }

    /**
     * Masks file path for logging (shows only directory structure, not full path).
     */
    private String maskPath(String filePath) {
        if (filePath == null || filePath.length() < 30) {
            return filePath;
        }
        try {
            // Show first 30 chars and last 10 chars
            return filePath.substring(0, 30) + "..." + filePath.substring(Math.max(30, filePath.length() - 10));
        } catch (Exception e) {
            return "***";
        }
    }

    /**
     * Masks URL for logging (shows only domain, not full path).
     */
    private String maskUrl(String url) {
        if (url == null || url.length() < 20) {
            return "***";
        }
        try {
            int pathStart = url.indexOf('/', 8); // Skip https://
            if (pathStart > 0) {
                return url.substring(0, pathStart) + "/***";
            }
        } catch (Exception e) {
            // Ignore
        }
        return url.substring(0, Math.min(20, url.length())) + "***";
    }
}

