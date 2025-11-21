package com.asp.service;

import com.asp.domain.ApiKey;
import com.asp.domain.ApiKeyRepository;
import com.asp.domain.Tenant;
import com.asp.domain.TenantRepository;
import com.asp.dto.ApiKeyResponse;
import com.asp.dto.CreateApiKeyRequest;
import com.asp.dto.CreateApiKeyResponse;
import com.asp.util.RandomStringGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyManagementService Unit Tests")
class ApiKeyManagementServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private ApiKeyManagementService apiKeyManagementService;

    private UUID testTenantId;
    private UUID testApiKeyId;
    private Tenant activeTenant;
    private Tenant suspendedTenant;
    private PasswordEncoder realPasswordEncoder;

    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();
        testApiKeyId = UUID.randomUUID();

        activeTenant = Tenant.builder()
                .id(testTenantId)
                .name("Test Tenant")
                .slug("test-tenant")
                .status(Tenant.TenantStatus.ACTIVE)
                .plan(Tenant.TenantPlan.PRO)
                .build();

        suspendedTenant = Tenant.builder()
                .id(testTenantId)
                .name("Suspended Tenant")
                .slug("suspended-tenant")
                .status(Tenant.TenantStatus.SUSPENDED)
                .plan(Tenant.TenantPlan.FREE)
                .build();

        realPasswordEncoder = new BCryptPasswordEncoder();
    }

    @Test
    @DisplayName("createApiKey - Success: Create live API key")
    void testCreateApiKey_Success_Live() {
        // Arrange
        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .tenantId(testTenantId)
                .displayName("Test API Key")
                .scopes(Set.of("read", "write"))
                .sandbox(false)
                .build();

        String generatedPrefix = "abc12345";
        String generatedSecret = "secret12345678901234567890123456";

        when(tenantRepository.findById(testTenantId))
                .thenReturn(Optional.of(activeTenant));
        when(apiKeyRepository.existsByPrefix(anyString()))
                .thenReturn(false);
        when(passwordEncoder.encode(anyString()))
                .thenAnswer(invocation -> realPasswordEncoder.encode(invocation.getArgument(0)));

        ApiKey savedApiKey = ApiKey.builder()
                .id(testApiKeyId)
                .tenant(activeTenant)
                .prefix(generatedPrefix)
                .keyHash("hashed_secret")
                .displayName("Test API Key")
                .sandbox(false)
                .scopes(Set.of("read", "write"))
                .build();

        when(apiKeyRepository.save(any(ApiKey.class)))
                .thenReturn(savedApiKey);

        try (MockedStatic<RandomStringGenerator> mockedGenerator = mockStatic(RandomStringGenerator.class)) {
            mockedGenerator.when(() -> RandomStringGenerator.generateAlphanumeric(8))
                    .thenReturn(generatedPrefix);
            mockedGenerator.when(() -> RandomStringGenerator.generateSecureSecret(32))
                    .thenReturn(generatedSecret);

            // Act
            CreateApiKeyResponse response = apiKeyManagementService.createApiKey(request);

            // Assert
            assertNotNull(response);
            assertEquals(testApiKeyId, response.getId());
            assertEquals(generatedPrefix, response.getPrefix());
            assertTrue(response.getApiKey().startsWith("asp_live_"));
            assertTrue(response.getApiKey().contains(generatedPrefix));
            assertTrue(response.getApiKey().contains(generatedSecret));

            verify(tenantRepository, times(1)).findById(testTenantId);
            verify(apiKeyRepository, times(1)).existsByPrefix(generatedPrefix);
            verify(apiKeyRepository, times(1)).save(any(ApiKey.class));
            verify(passwordEncoder, times(1)).encode(generatedSecret);
        }
    }

    @Test
    @DisplayName("createApiKey - Success: Create sandbox API key")
    void testCreateApiKey_Success_Sandbox() {
        // Arrange
        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .tenantId(testTenantId)
                .displayName("Sandbox Key")
                .scopes(Set.of("read"))
                .sandbox(true)
                .build();

        String generatedPrefix = "sand1234";
        String generatedSecret = "secret12345678901234567890123456";

        when(tenantRepository.findById(testTenantId))
                .thenReturn(Optional.of(activeTenant));
        when(apiKeyRepository.existsByPrefix(anyString()))
                .thenReturn(false);
        when(passwordEncoder.encode(anyString()))
                .thenAnswer(invocation -> realPasswordEncoder.encode(invocation.getArgument(0)));

        ApiKey savedApiKey = ApiKey.builder()
                .id(testApiKeyId)
                .tenant(activeTenant)
                .prefix(generatedPrefix)
                .keyHash("hashed_secret")
                .displayName("Sandbox Key")
                .sandbox(true)
                .scopes(Set.of("read"))
                .build();

        when(apiKeyRepository.save(any(ApiKey.class)))
                .thenReturn(savedApiKey);

        try (MockedStatic<RandomStringGenerator> mockedGenerator = mockStatic(RandomStringGenerator.class)) {
            mockedGenerator.when(() -> RandomStringGenerator.generateAlphanumeric(8))
                    .thenReturn(generatedPrefix);
            mockedGenerator.when(() -> RandomStringGenerator.generateSecureSecret(32))
                    .thenReturn(generatedSecret);

            // Act
            CreateApiKeyResponse response = apiKeyManagementService.createApiKey(request);

            // Assert
            assertNotNull(response);
            assertTrue(response.getApiKey().startsWith("asp_sandbox_"));
            assertTrue(response.getApiKey().contains(generatedPrefix));
            assertTrue(response.getApiKey().contains(generatedSecret));
        }
    }

    @Test
    @DisplayName("createApiKey - Exception: Tenant not found")
    void testCreateApiKey_TenantNotFound() {
        // Arrange
        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .tenantId(testTenantId)
                .displayName("Test API Key")
                .scopes(Set.of("read"))
                .build();

        when(tenantRepository.findById(testTenantId))
                .thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            apiKeyManagementService.createApiKey(request);
        });

        assertTrue(exception.getMessage().contains("Tenant not found"));
        verify(tenantRepository, times(1)).findById(testTenantId);
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("createApiKey - Exception: Tenant not ACTIVE")
    void testCreateApiKey_TenantNotActive() {
        // Arrange
        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .tenantId(testTenantId)
                .displayName("Test API Key")
                .scopes(Set.of("read"))
                .build();

        when(tenantRepository.findById(testTenantId))
                .thenReturn(Optional.of(suspendedTenant));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            apiKeyManagementService.createApiKey(request);
        });

        assertTrue(exception.getMessage().contains("Tenant is not ACTIVE"));
        verify(tenantRepository, times(1)).findById(testTenantId);
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    @DisplayName("createApiKey - Success: Prefix collision handling")
    void testCreateApiKey_PrefixCollision() {
        // Arrange
        CreateApiKeyRequest request = CreateApiKeyRequest.builder()
                .tenantId(testTenantId)
                .displayName("Test API Key")
                .scopes(Set.of("read"))
                .build();

        String firstPrefix = "collision";
        String secondPrefix = "unique12";

        when(tenantRepository.findById(testTenantId))
                .thenReturn(Optional.of(activeTenant));
        when(apiKeyRepository.existsByPrefix(firstPrefix))
                .thenReturn(true);
        when(apiKeyRepository.existsByPrefix(secondPrefix))
                .thenReturn(false);
        when(passwordEncoder.encode(anyString()))
                .thenAnswer(invocation -> realPasswordEncoder.encode(invocation.getArgument(0)));

        ApiKey savedApiKey = ApiKey.builder()
                .id(testApiKeyId)
                .tenant(activeTenant)
                .prefix(secondPrefix)
                .keyHash("hashed_secret")
                .displayName("Test API Key")
                .sandbox(false)
                .scopes(Set.of("read"))
                .build();

        when(apiKeyRepository.save(any(ApiKey.class)))
                .thenReturn(savedApiKey);

        try (MockedStatic<RandomStringGenerator> mockedGenerator = mockStatic(RandomStringGenerator.class)) {
            mockedGenerator.when(() -> RandomStringGenerator.generateAlphanumeric(8))
                    .thenReturn(firstPrefix, secondPrefix);
            mockedGenerator.when(() -> RandomStringGenerator.generateSecureSecret(32))
                    .thenReturn("secret12345678901234567890123456");

            // Act
            CreateApiKeyResponse response = apiKeyManagementService.createApiKey(request);

            // Assert
            assertNotNull(response);
            assertEquals(secondPrefix, response.getPrefix());
            verify(apiKeyRepository, times(2)).existsByPrefix(anyString());
        }
    }

    @Test
    @DisplayName("listApiKeys - Success: Return list of API keys")
    void testListApiKeys_Success() {
        // Arrange
        OffsetDateTime createdAt = OffsetDateTime.now();
        OffsetDateTime lastUsedAt = OffsetDateTime.now().minusHours(1);

        List<ApiKey> apiKeys = Arrays.asList(
                ApiKey.builder()
                        .id(testApiKeyId)
                        .tenant(activeTenant)
                        .prefix("prefix1")
                        .keyHash("hash1")
                        .displayName("Key 1")
                        .sandbox(false)
                        .scopes(Set.of("read", "write"))
                        .createdAt(createdAt)
                        .lastUsedAt(lastUsedAt)
                        .build(),
                ApiKey.builder()
                        .id(UUID.randomUUID())
                        .tenant(activeTenant)
                        .prefix("prefix2")
                        .keyHash("hash2")
                        .displayName("Key 2")
                        .sandbox(true)
                        .scopes(Set.of("read"))
                        .createdAt(createdAt)
                        .lastUsedAt(null)
                        .build()
        );

        when(apiKeyRepository.findByTenantId(testTenantId))
                .thenReturn(apiKeys);

        // Act
        List<ApiKeyResponse> responses = apiKeyManagementService.listApiKeys(testTenantId);

        // Assert
        assertNotNull(responses);
        assertEquals(2, responses.size());

        ApiKeyResponse first = responses.get(0);
        assertEquals(testApiKeyId, first.getId());
        assertEquals("Key 1", first.getDisplayName());
        assertEquals("prefix1", first.getPrefix());
        assertEquals(Set.of("read", "write"), first.getScopes());
        assertEquals(false, first.getSandbox());
        assertEquals(createdAt, first.getCreatedAt());
        assertEquals(lastUsedAt, first.getLastUsedAt());

        ApiKeyResponse second = responses.get(1);
        assertEquals("Key 2", second.getDisplayName());
        assertEquals(true, second.getSandbox());
        assertNull(second.getLastUsedAt());

        verify(apiKeyRepository, times(1)).findByTenantId(testTenantId);
    }

    @Test
    @DisplayName("listApiKeys - Success: Empty list")
    void testListApiKeys_EmptyList() {
        // Arrange
        when(apiKeyRepository.findByTenantId(testTenantId))
                .thenReturn(Collections.emptyList());

        // Act
        List<ApiKeyResponse> responses = apiKeyManagementService.listApiKeys(testTenantId);

        // Assert
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
        verify(apiKeyRepository, times(1)).findByTenantId(testTenantId);
    }

    @Test
    @DisplayName("deleteApiKey - Success: Delete API key")
    void testDeleteApiKey_Success() {
        // Arrange
        ApiKey apiKey = ApiKey.builder()
                .id(testApiKeyId)
                .tenant(activeTenant)
                .prefix("prefix1")
                .keyHash("hash1")
                .displayName("Key 1")
                .sandbox(false)
                .scopes(Set.of("read"))
                .build();

        when(apiKeyRepository.findById(testApiKeyId))
                .thenReturn(Optional.of(apiKey));
        doNothing().when(apiKeyRepository).delete(apiKey);

        // Act
        assertDoesNotThrow(() -> {
            apiKeyManagementService.deleteApiKey(testApiKeyId, testTenantId);
        });

        // Assert
        verify(apiKeyRepository, times(1)).findById(testApiKeyId);
        verify(apiKeyRepository, times(1)).delete(apiKey);
    }

    @Test
    @DisplayName("deleteApiKey - Exception: API key not found")
    void testDeleteApiKey_NotFound() {
        // Arrange
        when(apiKeyRepository.findById(testApiKeyId))
                .thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            apiKeyManagementService.deleteApiKey(testApiKeyId, testTenantId);
        });

        assertTrue(exception.getMessage().contains("API key not found"));
        verify(apiKeyRepository, times(1)).findById(testApiKeyId);
        verify(apiKeyRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteApiKey - Exception: Tenant mismatch")
    void testDeleteApiKey_TenantMismatch() {
        // Arrange
        UUID otherTenantId = UUID.randomUUID();
        Tenant otherTenant = Tenant.builder()
                .id(otherTenantId)
                .name("Other Tenant")
                .slug("other-tenant")
                .status(Tenant.TenantStatus.ACTIVE)
                .plan(Tenant.TenantPlan.PRO)
                .build();

        ApiKey apiKey = ApiKey.builder()
                .id(testApiKeyId)
                .tenant(otherTenant)
                .prefix("prefix1")
                .keyHash("hash1")
                .displayName("Key 1")
                .sandbox(false)
                .scopes(Set.of("read"))
                .build();

        when(apiKeyRepository.findById(testApiKeyId))
                .thenReturn(Optional.of(apiKey));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            apiKeyManagementService.deleteApiKey(testApiKeyId, testTenantId);
        });

        assertTrue(exception.getMessage().contains("API key does not belong to tenant"));
        verify(apiKeyRepository, times(1)).findById(testApiKeyId);
        verify(apiKeyRepository, never()).delete(any());
    }
}

