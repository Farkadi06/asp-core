package com.asp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * Configuration for AWS SQS client and queue URL resolution.
 * Supports both local/dev mode (via environment variable) and production (via AWS Secrets Manager).
 */
@Configuration
@Slf4j
public class SqsConfig {

    private static final String QUEUE_URL_SECRET_KEY = "ASP_SQS_INGESTION_QUEUE_URL";
    private static final String QUEUE_URL_ENV_VAR = "ASP_SQS_INGESTION_QUEUE_URL";

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    @Value("${aws.secrets-manager.secret-name:}")
    private String secretsManagerSecretName;

    /**
     * Creates SqsClient bean for SQS operations.
     * Uses default credential provider chain (environment variables, IAM roles, etc.).
     */
    @Bean
    public SqsClient sqsClient() {
        Region region = Region.of(awsRegion);
        log.info("Creating SqsClient with region: {}", region);
        
        SqsClient client = SqsClient.builder()
            .region(region)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();
        
        log.info("SqsClient created successfully");
        return client;
    }

    /**
     * Resolves queue URL from environment variable (local/dev) or AWS Secrets Manager (production).
     * Priority:
     * 1. Environment variable ASP_SQS_INGESTION_QUEUE_URL
     * 2. AWS Secrets Manager (if secret-name is configured)
     * 
     * Returns null if queue URL is not configured, allowing the app to start without SQS.
     * SQS publishing will fail gracefully if queue URL is not available.
     */
    @Bean(name = "sqsIngestionQueueUrl")
    public String queueUrl() {
        // First, try environment variable (local/dev mode)
        String envQueueUrl = System.getenv(QUEUE_URL_ENV_VAR);
        if (envQueueUrl != null && !envQueueUrl.trim().isEmpty()) {
            log.info("Using queue URL from environment variable: {}", maskUrl(envQueueUrl));
            return envQueueUrl.trim();
        }

        // If no env var, try AWS Secrets Manager (production mode)
        if (secretsManagerSecretName != null && !secretsManagerSecretName.trim().isEmpty()) {
            log.info("Attempting to retrieve queue URL from AWS Secrets Manager: {}", secretsManagerSecretName);
            try {
                String queueUrl = getQueueUrlFromSecretsManager();
                if (queueUrl != null && !queueUrl.trim().isEmpty()) {
                    log.info("Retrieved queue URL from Secrets Manager: {}", maskUrl(queueUrl));
                    return queueUrl.trim();
                }
            } catch (Exception e) {
                log.error("Failed to retrieve queue URL from AWS Secrets Manager: {}", secretsManagerSecretName, e);
                log.warn("SQS queue URL not available. SQS publishing will be disabled. " +
                    "Set {} environment variable or configure aws.secrets-manager.secret-name to enable SQS publishing.",
                    QUEUE_URL_ENV_VAR);
                return ""; // Return empty string instead of null so Spring can create the bean
            }
        }

        log.warn("SQS queue URL not configured. SQS publishing will be disabled. " +
            "Set {} environment variable or configure aws.secrets-manager.secret-name to enable SQS publishing.",
            QUEUE_URL_ENV_VAR);
        return ""; // Return empty string instead of null so Spring can create the bean
    }

    /**
     * Retrieves queue URL from AWS Secrets Manager.
     * Expected secret format: {"ASP_SQS_INGESTION_QUEUE_URL": "https://..."}
     * or plain string value.
     */
    private String getQueueUrlFromSecretsManager() {
        Region region = Region.of(awsRegion);
        
        try (SecretsManagerClient secretsClient = SecretsManagerClient.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build()) {
            
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(secretsManagerSecretName)
                .build();

            GetSecretValueResponse response = secretsClient.getSecretValue(request);
            String secretString = response.secretString();

            // Try to parse as JSON first
            if (secretString != null && secretString.trim().startsWith("{")) {
                // JSON format: extract the queue URL value
                // Simple JSON parsing (assuming format: {"ASP_SQS_INGESTION_QUEUE_URL": "..."})
                int keyIndex = secretString.indexOf("\"" + QUEUE_URL_SECRET_KEY + "\"");
                if (keyIndex >= 0) {
                    int colonIndex = secretString.indexOf(':', keyIndex);
                    int valueStart = secretString.indexOf('"', colonIndex) + 1;
                    int valueEnd = secretString.indexOf('"', valueStart);
                    if (valueEnd > valueStart) {
                        return secretString.substring(valueStart, valueEnd);
                    }
                }
                log.warn("JSON secret found but could not parse queue URL. Using entire secret as URL.");
            }

            // If not JSON or parsing failed, use entire secret as URL
            return secretString;
            
        } catch (Exception e) {
            log.error("Error retrieving secret from AWS Secrets Manager", e);
            throw new RuntimeException("Failed to retrieve queue URL from Secrets Manager", e);
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
            // Handle both http:// and https://
            int protocolEnd = url.indexOf("://");
            if (protocolEnd > 0) {
                int pathStart = url.indexOf('/', protocolEnd + 3);
                if (pathStart > 0) {
                    return url.substring(0, pathStart) + "/***";
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return url.substring(0, Math.min(20, url.length())) + "***";
    }
}

