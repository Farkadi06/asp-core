package com.asp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankResponse {
    private UUID id;
    private String name;
    private String slug;
    private String country;
    private String connectionType;
    private String iconUrl;
}

