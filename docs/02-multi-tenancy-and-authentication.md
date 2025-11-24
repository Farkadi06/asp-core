# Multi-Tenancy & API Key Authentication

## Overview

The platform implements a robust multi-tenant architecture with API key-based authentication. Each tenant is isolated at both the application and database levels using Row Level Security (RLS).

## Architecture Components

### 1. Tenant Context (ThreadLocal)

**File**: `src/main/java/com/asp/tenant/TenantContext.java`

ThreadLocal-based context management:
- `setTenantId(UUID tenantId)` - Sets tenant ID for current thread
- `getTenantId()` - Retrieves current tenant ID
- `clear()` - Cleans up tenant context (called after request)

**Usage**: Automatically set by `ApiKeyAuthenticationFilter` after successful authentication.

### 2. API Key Authentication Filter

**File**: `src/main/java/com/asp/security/ApiKeyAuthenticationFilter.java`

Custom Spring Security filter that:
1. Reads `X-Api-Key` header from request
2. Parses API key token (format: `asp_live_<prefix>_<secret>` or `asp_sandbox_<prefix>_<secret>`)
3. Validates API key via `ApiKeyService`
4. Sets `TenantContext` with tenant ID
5. Creates `UsernamePasswordAuthenticationToken` with `ROLE_API_KEY` authority
6. Stores authentication in `SecurityContextHolder`
7. Clears context after request completion

**Error Handling**: Returns 401 JSON response for invalid keys:
```json
{
  "error": "invalid_api_key"
}
```

### 3. API Key Token Parser

**File**: `src/main/java/com/asp/security/ApiKeyToken.java`

Parses raw API keys:
- **Live keys**: `asp_live_<prefix>_<secret>`
- **Sandbox keys**: `asp_sandbox_<prefix>_<secret>`
- Extracts prefix and secret for validation
- Throws `IllegalArgumentException` for invalid formats

### 4. API Key Service

**File**: `src/main/java/com/asp/security/ApiKeyService.java`

Validates API keys:
1. Looks up `ApiKey` entity by prefix
2. Validates secret using BCrypt `PasswordEncoder.matches()`
3. Checks tenant status (must be ACTIVE)
4. Updates `lastUsedAt` timestamp
5. Returns validation result with `tenantId`, `sandbox` flag, and `scopes`

### 5. Tenant Transaction Interceptor

**File**: `src/main/java/com/asp/config/TenantTransactionInterceptor.java`

AOP aspect that sets PostgreSQL session variable:
- Executes `SET LOCAL asp.tenant_id = '<tenantId>'` at transaction start
- Ensures Row Level Security (RLS) policies apply correctly
- Automatically cleared at transaction end

## Database Schema

### Tenant Table

**Migration**: `V1__tenant_and_api_key_tables.sql`

```sql
CREATE TABLE tenant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    slug VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    plan VARCHAR(50) NOT NULL CHECK (plan IN ('FREE', 'PRO', 'ENTERPRISE')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**Status Values**:
- `ACTIVE` - Tenant can use the platform
- `SUSPENDED` - Tenant access is blocked

**Plan Values**:
- `FREE` - Free tier
- `PRO` - Professional tier
- `ENTERPRISE` - Enterprise tier

### API Key Tables

```sql
CREATE TABLE api_key (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    prefix VARCHAR(20) NOT NULL UNIQUE,
    key_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    sandbox BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE
);

CREATE TABLE api_key_scope (
    api_key_id UUID NOT NULL REFERENCES api_key(id) ON DELETE CASCADE,
    scope VARCHAR(255) NOT NULL,
    PRIMARY KEY (api_key_id, scope)
);
```

## Row Level Security (RLS)

**Migration**: `V2__enable_rls_and_policies.sql`

Enabled on `api_key` table:
```sql
ALTER TABLE api_key ENABLE ROW LEVEL SECURITY;

CREATE POLICY api_key_tenant_isolation ON api_key
    USING (tenant_id = current_setting('asp.tenant_id', true)::uuid);
```

**How it works**:
1. `ApiKeyAuthenticationFilter` sets tenant ID in `TenantContext`
2. `TenantTransactionInterceptor` sets PostgreSQL session variable `asp.tenant_id`
3. RLS policy automatically filters queries to only show tenant's own API keys
4. Applies to all tables with RLS enabled

## Security Configuration

**File**: `src/main/java/com/asp/config/SecurityConfig.java`

Key settings:
- **Sessions**: Stateless (no session cookies)
- **CSRF**: Disabled (stateless API)
- **Filter Order**: `ApiKeyAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`
- **Protected Routes**: All `/v1/**` endpoints require authentication
- **Public Routes**: `/actuator/health`, `/v1/ping` (for testing)

## API Key Format

**Live Key Example**:
```
asp_live_abc12345_xYz789SecretKey12345678901234567890
```

**Sandbox Key Example**:
```
asp_sandbox_abc12345_xYz789SecretKey12345678901234567890
```

**Structure**:
- Prefix: `asp_` (base), `asp_live_` or `asp_sandbox_` (type)
- Prefix: 8-character alphanumeric identifier
- Secret: 32-byte secure random value (Base64 encoded)

## Authentication Flow

```
1. Client sends request with X-Api-Key header
   ↓
2. ApiKeyAuthenticationFilter intercepts request
   ↓
3. ApiKeyToken.parse() extracts prefix and secret
   ↓
4. ApiKeyService.validateApiKey() checks:
   - Prefix exists in database
   - Secret matches BCrypt hash
   - Tenant is ACTIVE
   ↓
5. TenantContext.setTenantId() stores tenant ID
   ↓
6. TenantTransactionInterceptor sets PostgreSQL session variable
   ↓
7. SecurityContextHolder.setAuthentication() creates authenticated session
   ↓
8. Request proceeds to controller
   ↓
9. TenantContext.clear() after request completion
```

## Testing Endpoint

**Endpoint**: `GET /v1/ping`

Returns tenant ID from context:
```json
{
  "status": "ok",
  "tenantId": "550e8400-e29b-41d4-a716-446655440000"
}
```

Used to verify authentication and tenant context are working correctly.

