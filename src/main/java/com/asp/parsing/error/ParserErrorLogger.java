package com.asp.parsing.error;

import com.asp.domain.Ingestion;
import com.asp.domain.ParserError;
import com.asp.domain.ParserErrorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper for logging parsing errors to the database.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ParserErrorLogger {

    private final ParserErrorRepository parserErrorRepository;

    /**
     * Logs an error to the database.
     *
     * @param ingestion the ingestion
     * @param category the error category
     * @param errorCode the error code
     * @param errorMessage the error message
     * @param throwable optional exception
     * @param context optional context data
     */
    public void logError(Ingestion ingestion, ParserError.ErrorCategory category, 
                        String errorCode, String errorMessage, Throwable throwable, 
                        Map<String, Object> context) {
        log.error("[INGESTION:{}] [ERROR:{}] {}: {}", 
            ingestion.getId(), errorCode, category, errorMessage, throwable);

        String stackTrace = null;
        if (throwable != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            throwable.printStackTrace(pw);
            stackTrace = sw.toString();
        }

        Map<String, Object> errorContext = context != null ? new HashMap<>(context) : new HashMap<>();
        errorContext.put("ingestionId", ingestion.getId().toString());
        errorContext.put("tenantId", ingestion.getTenant().getId().toString());

        ParserError error = ParserError.builder()
            .ingestion(ingestion)
            .tenant(ingestion.getTenant())
            .errorCategory(category)
            .errorCode(errorCode)
            .errorMessage(errorMessage)
            .stackTrace(stackTrace)
            .context(errorContext)
            .build();

        parserErrorRepository.save(error);
    }

    /**
     * Logs an error without exception.
     */
    public void logError(Ingestion ingestion, ParserError.ErrorCategory category, 
                        String errorCode, String errorMessage, Map<String, Object> context) {
        logError(ingestion, category, errorCode, errorMessage, null, context);
    }

    /**
     * Logs an error with exception.
     */
    public void logError(Ingestion ingestion, ParserError.ErrorCategory category, 
                        String errorCode, String errorMessage, Throwable throwable) {
        logError(ingestion, category, errorCode, errorMessage, throwable, null);
    }
}

