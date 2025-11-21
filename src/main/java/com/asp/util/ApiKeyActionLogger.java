package com.asp.util;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class ApiKeyActionLogger {

    private static final String LOG_FILE = "logs/api-key-actions.log";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private static final Logger logger = LoggerFactory.getLogger(ApiKeyActionLogger.class);

    static {
        // Ensure logs directory exists
        File logDir = new File("logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
    }

    public static void logCreate(UUID tenantId, UUID apiKeyId, String prefix, Set<String> scopes) {
        String scopesStr = scopes != null && !scopes.isEmpty() 
            ? scopes.toString() 
            : "[]";
        
        String message = String.format(
            "[%s] CREATE tenant=%s apiKey=%s prefix=%s scopes=%s",
            OffsetDateTime.now().format(DATE_FORMATTER),
            tenantId,
            apiKeyId,
            prefix,
            scopesStr
        );
        
        writeToFile(message);
    }

    public static void logDelete(UUID apiKeyId) {
        String message = String.format(
            "[%s] DELETE apiKey=%s",
            OffsetDateTime.now().format(DATE_FORMATTER),
            apiKeyId
        );
        
        writeToFile(message);
    }

    private static void writeToFile(String message) {
        try (FileWriter writer = new FileWriter(LOG_FILE, true)) {
            writer.write(message + System.lineSeparator());
            writer.flush();
        } catch (IOException e) {
            logger.error("Failed to write to API key action log file: {}", LOG_FILE, e);
        }
    }
}

