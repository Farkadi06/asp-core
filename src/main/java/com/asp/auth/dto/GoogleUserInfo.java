package com.asp.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for Google user information extracted from ID token.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleUserInfo {
    private String email;
    private boolean emailVerified;
    private String displayName;
    private String googleUserId; // "sub" claim from ID token
}

