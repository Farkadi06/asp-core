package com.asp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for API key deletion.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeleteApiKeyResponse {
    private boolean deleted;
}
