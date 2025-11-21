package com.asp.api;

import com.asp.dto.ApiKeyResponse;
import com.asp.dto.CreateApiKeyRequest;
import com.asp.dto.CreateApiKeyResponse;
import com.asp.service.ApiKeyManagementService;
import com.asp.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiKeyController.class)
@DisplayName("ApiKeyController Unit Tests")
class ApiKeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ApiKeyManagementService apiKeyManagementService;

    private UUID testTenantId;
    private UUID testApiKeyId;

    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();
        testApiKeyId = UUID.randomUUID();
        TenantContext.setTenantId(testTenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("POST /v1/api-keys - Success: Create API key with valid request")
    @WithMockUser
    void testCreateApiKey_Success() throws Exception {
        // Arrange
        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .tenantId(testTenantId)
                .displayName("Test API Key")
                .scopes(Set.of("read", "write"))
                .sandbox(false)
                .build();

        CreateApiKeyResponse response = CreateApiKeyResponse.builder()
                .id(testApiKeyId)
                .prefix("abc12345")
                .apiKey("asp_live_abc12345_secret12345678901234567890")
                .build();

        when(apiKeyManagementService.createApiKey(any(CreateApiKeyRequest.class)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(testApiKeyId.toString()))
                .andExpect(jsonPath("$.prefix").value("abc12345"))
                .andExpect(jsonPath("$.prefix").value(hasLength(8)))
                .andExpect(jsonPath("$.apiKey").value("asp_live_abc12345_secret12345678901234567890"));

        verify(apiKeyManagementService, times(1)).createApiKey(any(CreateApiKeyRequest.class));
    }

    @Test
    @DisplayName("POST /v1/api-keys - Success: Create sandbox API key")
    @WithMockUser
    void testCreateApiKey_Sandbox() throws Exception {
        // Arrange
        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .tenantId(testTenantId)
                .displayName("Sandbox Key")
                .scopes(Set.of("read"))
                .sandbox(true)
                .build();

        CreateApiKeyResponse response = CreateApiKeyResponse.builder()
                .id(testApiKeyId)
                .prefix("sand1234")
                .apiKey("asp_sandbox_sand1234_secret12345678901234567890")
                .build();

        when(apiKeyManagementService.createApiKey(any(CreateApiKeyRequest.class)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(testApiKeyId.toString()))
                .andExpect(jsonPath("$.prefix").value("sand1234"))
                .andExpect(jsonPath("$.apiKey").value("asp_sandbox_sand1234_secret12345678901234567890"));

        verify(apiKeyManagementService, times(1)).createApiKey(any(CreateApiKeyRequest.class));
    }

    @Test
    @DisplayName("POST /v1/api-keys - Bad Request: Missing tenantId")
    @WithMockUser
    void testCreateApiKey_MissingTenantId() throws Exception {
        // Arrange
        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .displayName("Test API Key")
                .scopes(Set.of("read"))
                .build();

        // Act & Assert
        mockMvc.perform(post("/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(apiKeyManagementService, never()).createApiKey(any());
    }

    @Test
    @DisplayName("POST /v1/api-keys - Bad Request: Missing displayName")
    @WithMockUser
    void testCreateApiKey_MissingDisplayName() throws Exception {
        // Arrange
        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .tenantId(testTenantId)
                .scopes(Set.of("read"))
                .build();

        // Act & Assert
        mockMvc.perform(post("/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(apiKeyManagementService, never()).createApiKey(any());
    }

    @Test
    @DisplayName("POST /v1/api-keys - Bad Request: Empty scopes")
    @WithMockUser
    void testCreateApiKey_EmptyScopes() throws Exception {
        // Arrange
        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .tenantId(testTenantId)
                .displayName("Test API Key")
                .scopes(Collections.emptySet())
                .build();

        // Act & Assert
        mockMvc.perform(post("/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(apiKeyManagementService, never()).createApiKey(any());
    }

    @Test
    @DisplayName("POST /v1/api-keys - Not Found: Invalid tenant")
    @WithMockUser
    void testCreateApiKey_InvalidTenant() throws Exception {
        // Arrange
        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .tenantId(testTenantId)
                .displayName("Test API Key")
                .scopes(Set.of("read"))
                .build();

        when(apiKeyManagementService.createApiKey(any(CreateApiKeyRequest.class)))
                .thenThrow(new IllegalArgumentException("Tenant not found: " + testTenantId));

        // Act & Assert
        mockMvc.perform(post("/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());

        verify(apiKeyManagementService, times(1)).createApiKey(any(CreateApiKeyRequest.class));
    }

    @Test
    @DisplayName("POST /v1/api-keys - Forbidden: Tenant inactive")
    @WithMockUser
    void testCreateApiKey_TenantInactive() throws Exception {
        // Arrange
        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .tenantId(testTenantId)
                .displayName("Test API Key")
                .scopes(Set.of("read"))
                .build();

        when(apiKeyManagementService.createApiKey(any(CreateApiKeyRequest.class)))
                .thenThrow(new IllegalStateException("Tenant is not ACTIVE: " + testTenantId));

        // Act & Assert
        mockMvc.perform(post("/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());

        verify(apiKeyManagementService, times(1)).createApiKey(any(CreateApiKeyRequest.class));
    }

    @Test
    @DisplayName("POST /v1/api-keys - Internal Server Error: Service exception")
    @WithMockUser
    void testCreateApiKey_ServiceException() throws Exception {
        // Arrange
        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .tenantId(testTenantId)
                .displayName("Test API Key")
                .scopes(Set.of("read"))
                .build();

        when(apiKeyManagementService.createApiKey(any(CreateApiKeyRequest.class)))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        mockMvc.perform(post("/v1/api-keys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());

        verify(apiKeyManagementService, times(1)).createApiKey(any(CreateApiKeyRequest.class));
    }

    @Test
    @DisplayName("GET /v1/api-keys - Success: List API keys")
    @WithMockUser
    void testListApiKeys_Success() throws Exception {
        // Arrange
        OffsetDateTime createdAt = OffsetDateTime.now();
        OffsetDateTime lastUsedAt = OffsetDateTime.now().minusHours(1);

        List<ApiKeyResponse> apiKeys = Arrays.asList(
                ApiKeyResponse.builder()
                        .id(testApiKeyId)
                        .displayName("Key 1")
                        .prefix("prefix1")
                        .scopes(Set.of("read", "write"))
                        .sandbox(false)
                        .createdAt(createdAt)
                        .lastUsedAt(lastUsedAt)
                        .build(),
                ApiKeyResponse.builder()
                        .id(UUID.randomUUID())
                        .displayName("Key 2")
                        .prefix("prefix2")
                        .scopes(Set.of("read"))
                        .sandbox(true)
                        .createdAt(createdAt)
                        .lastUsedAt(null)
                        .build()
        );

        when(apiKeyManagementService.listApiKeys(testTenantId))
                .thenReturn(apiKeys);

        // Act & Assert
        mockMvc.perform(get("/v1/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(testApiKeyId.toString()))
                .andExpect(jsonPath("$[0].displayName").value("Key 1"))
                .andExpect(jsonPath("$[0].prefix").value("prefix1"))
                .andExpect(jsonPath("$[0].scopes").isArray())
                .andExpect(jsonPath("$[0].scopes", hasSize(2)))
                .andExpect(jsonPath("$[0].sandbox").value(false))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].lastUsedAt").exists())
                .andExpect(jsonPath("$[1].id").exists())
                .andExpect(jsonPath("$[1].displayName").value("Key 2"))
                .andExpect(jsonPath("$[1].prefix").value("prefix2"))
                .andExpect(jsonPath("$[1].sandbox").value(true))
                .andExpect(jsonPath("$[1].lastUsedAt").isEmpty());

        verify(apiKeyManagementService, times(1)).listApiKeys(testTenantId);
    }

    @Test
    @DisplayName("GET /v1/api-keys - Success: Empty list")
    @WithMockUser
    void testListApiKeys_EmptyList() throws Exception {
        // Arrange
        when(apiKeyManagementService.listApiKeys(testTenantId))
                .thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/v1/api-keys"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(apiKeyManagementService, times(1)).listApiKeys(testTenantId);
    }

    @Test
    @DisplayName("GET /v1/api-keys - Unauthorized: Tenant context not set")
    @WithMockUser
    void testListApiKeys_NoTenantContext() throws Exception {
        // Arrange
        TenantContext.clear();

        // Act & Assert
        mockMvc.perform(get("/v1/api-keys"))
                .andExpect(status().isUnauthorized());

        verify(apiKeyManagementService, never()).listApiKeys(any());
    }

    @Test
    @DisplayName("DELETE /v1/api-keys/{id} - Success: Delete API key")
    @WithMockUser
    void testDeleteApiKey_Success() throws Exception {
        // Arrange
        doNothing().when(apiKeyManagementService).deleteApiKey(testApiKeyId, testTenantId);

        // Act & Assert
        mockMvc.perform(delete("/v1/api-keys/{id}", testApiKeyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        verify(apiKeyManagementService, times(1)).deleteApiKey(testApiKeyId, testTenantId);
    }

    @Test
    @DisplayName("DELETE /v1/api-keys/{id} - Not Found: Unknown key")
    @WithMockUser
    void testDeleteApiKey_NotFound() throws Exception {
        // Arrange
        doThrow(new IllegalArgumentException("API key not found: " + testApiKeyId))
                .when(apiKeyManagementService).deleteApiKey(testApiKeyId, testTenantId);

        // Act & Assert
        mockMvc.perform(delete("/v1/api-keys/{id}", testApiKeyId))
                .andExpect(status().isInternalServerError());

        verify(apiKeyManagementService, times(1)).deleteApiKey(testApiKeyId, testTenantId);
    }

    @Test
    @DisplayName("DELETE /v1/api-keys/{id} - Forbidden: Tenant mismatch")
    @WithMockUser
    void testDeleteApiKey_TenantMismatch() throws Exception {
        // Arrange
        doThrow(new IllegalStateException("API key does not belong to tenant: " + testTenantId))
                .when(apiKeyManagementService).deleteApiKey(testApiKeyId, testTenantId);

        // Act & Assert
        mockMvc.perform(delete("/v1/api-keys/{id}", testApiKeyId))
                .andExpect(status().isInternalServerError());

        verify(apiKeyManagementService, times(1)).deleteApiKey(testApiKeyId, testTenantId);
    }

    @Test
    @DisplayName("DELETE /v1/api-keys/{id} - Unauthorized: Tenant context not set")
    @WithMockUser
    void testDeleteApiKey_NoTenantContext() throws Exception {
        // Arrange
        TenantContext.clear();

        // Act & Assert
        mockMvc.perform(delete("/v1/api-keys/{id}", testApiKeyId))
                .andExpect(status().isUnauthorized());

        verify(apiKeyManagementService, never()).deleteApiKey(any(), any());
    }

    @Test
    @DisplayName("DELETE /v1/api-keys/{id} - Internal Server Error: Service exception")
    @WithMockUser
    void testDeleteApiKey_ServiceException() throws Exception {
        // Arrange
        doThrow(new RuntimeException("Database error"))
                .when(apiKeyManagementService).deleteApiKey(testApiKeyId, testTenantId);

        // Act & Assert
        mockMvc.perform(delete("/v1/api-keys/{id}", testApiKeyId))
                .andExpect(status().isInternalServerError());

        verify(apiKeyManagementService, times(1)).deleteApiKey(testApiKeyId, testTenantId);
    }
}

