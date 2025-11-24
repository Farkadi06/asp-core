package com.asp.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Entity for storing parsing errors.
 */
@Entity
@Table(name = "parser_error")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParserError {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingestion_id", nullable = false)
    private Ingestion ingestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "error_category", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private ErrorCategory errorCategory;

    @Column(name = "error_code", nullable = false, length = 100)
    private String errorCode;

    @Column(name = "error_message", nullable = false, columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    private Map<String, Object> context = new HashMap<>();

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    public enum ErrorCategory {
        PDF_ERROR,
        DETECTION_ERROR,
        PARSING_ERROR,
        STORAGE_ERROR,
        UNKNOWN_FORMAT,
        VALIDATION_ERROR
    }
}

