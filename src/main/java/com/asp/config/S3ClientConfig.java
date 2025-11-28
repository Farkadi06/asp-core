package com.asp.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class S3ClientConfig {

    private static final Logger log = LoggerFactory.getLogger(S3ClientConfig.class);

    @Value("${AWS_REGION:eu-west-1}")
    private String awsRegion;

    @Value("${ASP_S3_BUCKET_NAME:}")
    private String bucketName;

    @PostConstruct
    public void logConfig() {
        if (bucketName == null || bucketName.isBlank()) {
            log.error("[CORE][S3] ASP_S3_BUCKET_NAME is not configured! S3 uploads will fail.");
        } else {
            log.info("[CORE][S3] Using S3 bucket: {}", bucketName);
        }
        log.info("[CORE][S3] AWS region: {}", awsRegion);
    }

    @Bean
    public S3Client aspCoreS3Client() {
        Region region = Region.of(awsRegion);
        log.info("[CORE][S3] Initializing S3Client with region: {}", region);
        return S3Client.builder()
                .region(region)
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean(name = "aspIngestionBucketName")
    public String aspIngestionBucketName() {
        return bucketName;
    }
}

