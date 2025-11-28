package com.asp.mapper;

import com.asp.domain.Ingestion;
import com.asp.shared.dto.IngestionMetadataDto;
import org.springframework.stereotype.Component;

@Component
public class IngestionMetadataMapper {

    public IngestionMetadataDto toDto(Ingestion ingestion) {
        if (ingestion == null) {
            return null;
        }

        return IngestionMetadataDto.builder()
            .ingestionId(ingestion.getId())
            .bankConnectionId(ingestion.getBankConnection() != null ? ingestion.getBankConnection().getId() : null)
            .tenantId(ingestion.getTenant() != null ? ingestion.getTenant().getId().toString() : null)
            .storagePath(ingestion.getStoragePath())
            .originalFileName(ingestion.getOriginalFileName())
            .ingestionType(ingestion.getIngestionType() != null ? ingestion.getIngestionType().name() : null)
            .source(ingestion.getSource() != null ? ingestion.getSource().name() : null)
            .fileSize(ingestion.getFileSize())
            .status(ingestion.getStatus() != null ? ingestion.getStatus().name() : null)
            .createdAt(ingestion.getCreatedAt())
            .updatedAt(ingestion.getUpdatedAt())
            .build();
    }
}

