package com.asp.platform.enrichment.domain;

import com.asp.domain.NormalizedTransaction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "enriched_transaction")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrichedTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "normalized_transaction_id", nullable = false)
    private NormalizedTransaction normalizedTransaction;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "bank_connection_id", nullable = false)
    private UUID bankConnectionId;

    @Column(name = "user_ref")
    private String userRef;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(name = "description_raw", nullable = false, columnDefinition = "TEXT")
    private String descriptionRaw;

    @Column(name = "description_clean", columnDefinition = "TEXT")
    private String descriptionClean;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "subcategory", length = 100)
    private String subcategory;

    @Column(name = "merchant_name", length = 255)
    private String merchantName;

    @Column(name = "merchant_normalized", length = 255)
    private String merchantNormalized;

    @Column(name = "is_salary", nullable = false)
    @Builder.Default
    private boolean salary = false;

    @Column(name = "is_recurring", nullable = false)
    @Builder.Default
    private boolean recurring = false;

    @Column(name = "recurring_group_id")
    private UUID recurringGroupId;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "amount_abs", nullable = false, precision = 18, scale = 2)
    private BigDecimal amountAbs;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "metadata", columnDefinition = "jsonb", nullable = false)
    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}

