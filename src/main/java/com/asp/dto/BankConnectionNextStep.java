package com.asp.dto;

import com.asp.domain.BankConnectionNextStepType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankConnectionNextStep {
    private BankConnectionNextStepType type;
    private Map<String, Object> details;
}

