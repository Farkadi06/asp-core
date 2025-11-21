package com.asp.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "bank")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bank {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String slug;

    @Column(nullable = false, length = 50)
    private String country;

    @Column(name = "connection_type", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private ConnectionType connectionType;

    @Column(name = "icon_url", length = 300)
    private String iconUrl;

    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private BankStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public enum ConnectionType {
        OAUTH,
        PDF,
        SCRAPER,
        UNKNOWN
    }

    public enum BankStatus {
        ACTIVE,
        INACTIVE
    }
}

