package com.asp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankConnectionResponse {
    private UUID connectionId;
    private UUID bankId;
    private String status;
    private String userRef;
    private BankConnectionNextStep nextStep;
    private Map<String, Object> metadata;
}

