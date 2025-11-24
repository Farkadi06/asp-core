# API Key Management

## Overview

Tenants can create, list, and delete API keys through REST endpoints. API keys are scoped with permissions and can be marked as sandbox or live keys.

## Endpoints

### 1. Create API Key

**Endpoint**: `POST /v1/api-keys`  
**Authentication**: Required (API key)

**Request Body**:
```json
{
  "tenantId": "550e8400-e29b-41d4-a716-446655440000",
  "displayName": "Local Dev Key",
  "scopes": ["read", "write"],
  "sandbox": false
}
```

**Response**: `201 CREATED`
```json
{
  "id": "abc12345-def6-7890-abcd-ef1234567890",
  "prefix": "abc12345",
  "apiKey": "asp_live_abc12345_xYz789SecretKey12345678901234567890"
}
```

**Important**: The full API key is returned only once. Store it securely immediately.

**Behavior**:
1. Validates tenant exists and is ACTIVE
2. Generates 8-character alphanumeric prefix (ensures uniqueness)
3. Generates 32-byte secure random secret
4. Constructs full key: `asp_live_<prefix>_<secret>` or `asp_sandbox_<prefix>_<secret>`
5. Hashes secret with BCrypt
6. Saves to `api_key` and `api_key_scope` tables
7. Logs creation to `logs/api-key-actions.log`

### 2. List API Keys

**Endpoint**: `GET /v1/api-keys`  
**Authentication**: Required (API key)

**Response**: `200 OK`
```json
[
  {
    "id": "abc12345-def6-7890-abcd-ef1234567890",
    "displayName": "Local Dev Key",
    "prefix": "abc12345",
    "scopes": ["read", "write"],
    "sandbox": false,
    "createdAt": "2025-11-22T10:00:00Z",
    "lastUsedAt": "2025-11-22T15:30:00Z"
  }
]
```

**Note**: Full API key secret is never returned in list responses (security best practice).

**Filtering**: Automatically filtered by tenant ID via RLS (tenants only see their own keys).

### 3. Delete API Key

**Endpoint**: `DELETE /v1/api-keys/{id}`  
**Authentication**: Required (API key)

**Response**: `200 OK`
```json
{
  "deleted": true
}
```

**Behavior**:
1. Validates API key belongs to current tenant
2. Hard deletes from `api_key_scope` (cascade) and `api_key` tables
3. Logs deletion to `logs/api-key-actions.log`

## Service Layer

**File**: `src/main/java/com/asp/service/ApiKeyManagementService.java`

**Methods**:
- `createApiKey(CreateApiKeyRequest request)` - Creates new API key
- `listApiKeys(UUID tenantId)` - Lists all keys for tenant
- `deleteApiKey(UUID apiKeyId, UUID tenantId)` - Deletes key

**Key Features**:
- **Prefix Uniqueness**: Retries up to 5 times if prefix collision occurs
- **Secure Generation**: Uses `SecureRandom` for cryptographically secure values
- **BCrypt Hashing**: Secrets are hashed before storage (never stored in plaintext)
- **Tenant Validation**: Ensures tenant exists and is ACTIVE before key creation

## DTOs

### CreateApiKeyRequest
```java
public class CreateApiKeyRequest {
    private UUID tenantId;           // Required
    private String displayName;      // Required, max 255 chars
    private Set<String> scopes;      // Required (e.g., ["read", "write"])
    private Boolean sandbox;         // Optional, default: false
}
```

### CreateApiKeyResponse
```java
public class CreateApiKeyResponse {
    private UUID id;
    private String prefix;
    private String apiKey;           // Full key (only returned once)
}
```

### ApiKeyResponse
```java
public class ApiKeyResponse {
    private UUID id;
    private String displayName;
    private String prefix;
    private Set<String> scopes;
    private Boolean sandbox;
    private OffsetDateTime createdAt;
    private OffsetDateTime lastUsedAt;
}
```

## Scopes

API keys can have multiple scopes for fine-grained access control:
- `read` - Read-only access
- `write` - Write access
- Custom scopes can be added as needed

**Future Enhancement**: Scope-based authorization can be implemented in Spring Security to restrict endpoint access.

## Logging

**File**: `logs/api-key-actions.log`

Format:
```
[2025-11-22T12:30:00] CREATE tenant=550e8400-... apiKey=abc12345-... prefix=abc12345 scopes=[read,write] sandbox=false
[2025-11-22T12:35:10] DELETE tenant=550e8400-... apiKey=abc12345-...
```

Used for audit trails and security monitoring.

## Security Considerations

1. **Never Log Secrets**: Only prefix and metadata are logged
2. **One-Time Display**: Full API key shown only on creation
3. **BCrypt Hashing**: Secrets hashed with cost factor 10
4. **Tenant Isolation**: RLS ensures tenants can only manage their own keys
5. **Hard Delete**: No soft delete - keys are permanently removed

## Example Usage

**Create Key**:
```bash
curl -X POST http://localhost:8080/v1/api-keys \
  -H "X-Api-Key: asp_live_existing_prefix_secret" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "550e8400-e29b-41d4-a716-446655440000",
    "displayName": "Production Key",
    "scopes": ["read", "write"],
    "sandbox": false
  }'
```

**List Keys**:
```bash
curl -X GET http://localhost:8080/v1/api-keys \
  -H "X-Api-Key: asp_live_abc12345_secret"
```

**Delete Key**:
```bash
curl -X DELETE http://localhost:8080/v1/api-keys/abc12345-def6-7890-abcd-ef1234567890 \
  -H "X-Api-Key: asp_live_abc12345_secret"
```

