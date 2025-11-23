package com.asp.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ingestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_connection_id", nullable = false)
    private BankConnection bankConnection;

    @Column(name = "user_ref", nullable = false)
    private String userRef;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private IngestionStatus status;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private IngestionSource source;

    @Column(name = "ingestion_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private IngestionType ingestionType;

    @Column(name = "original_file_name")
    private String originalFileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "storage_path")
    private String storagePath;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "processed_at")
    private Instant processedAt;
}

