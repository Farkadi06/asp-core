# Bank Directory & Connection Management

## Overview

The platform maintains a directory of supported banks and manages connections between tenants and banks. Each connection represents a tenant's attempt to link a bank account for data ingestion.

## Bank Directory

### Entity

**File**: `src/main/java/com/asp/domain/Bank.java`

**Fields**:
- `id` (UUID) - Primary key
- `name` (String) - Bank name
- `slug` (String) - Unique URL-friendly identifier
- `country` (String) - Country code
- `connectionType` (Enum) - How this bank connects
  - `OAUTH` - OAuth-based connection
  - `PDF` - PDF statement upload
  - `SCRAPER` - Credential-based scraping
  - `UNKNOWN` - Connection method not yet defined
- `iconUrl` (String) - Bank logo/icon URL
- `status` (Enum) - `ACTIVE` or `INACTIVE`
- `createdAt`, `updatedAt` - Timestamps

### Endpoint

**GET /v1/banks**

Lists all ACTIVE banks:

**Response**: `200 OK`
```json
[
  {
    "id": "bank-uuid-123",
    "name": "Bank of Morocco",
    "slug": "bank-of-morocco",
    "country": "MA",
    "connectionType": "OAUTH",
    "iconUrl": "https://example.com/icon.png"
  }
]
```

**Authentication**: Required (API key)

**Service**: `BankDirectoryService.listBanks()` - Returns only ACTIVE banks

## Bank Connections

### Entity

**File**: `src/main/java/com/asp/domain/BankConnection.java`

**Fields**:
- `id` (UUID) - Primary key
- `tenant` (Tenant) - FK to tenant table
- `bank` (Bank) - FK to bank table
- `userRef` (String) - Tenant-provided user identifier (required)
- `status` (Enum) - Connection status:
  - `PENDING` - Connection initiated
  - `WAITING_UPLOAD` - Waiting for PDF upload
  - `WAITING_OAUTH` - Waiting for OAuth completion
  - `CONNECTED` - Successfully connected
  - `FAILED` - Connection failed
- `metadata` (JSONB) - Flexible metadata storage
- `createdAt`, `updatedAt` - Timestamps

### Endpoints

#### 1. Create Bank Connection

**POST /v1/bank-connections/{bankId}/connect**

**Request Body**:
```json
{
  "userRef": "user_sara_001"
}
```

**Response**: `201 CREATED`
```json
{
  "connectionId": "connection-uuid-123",
  "bankId": "bank-uuid-123",
  "status": "WAITING_OAUTH",
  "userRef": "user_sara_001",
  "nextStep": {
    "type": "OAUTH_REDIRECT",
    "details": {
      "redirectUrl": "http://localhost:8080/v1/bank-connections/connection-uuid-123/oauth/start"
    }
  },
  "metadata": {}
}
```

**Behavior**:
1. Validates tenant exists
2. Validates bank exists and is ACTIVE
3. Creates connection with appropriate status based on `bank.connectionType`
4. Copies `userRef` from request
5. Computes `nextStep` based on connection type (not persisted)
6. Returns connection details with next action

#### 2. List Connections

**GET /v1/bank-connections?userRef={userRef}`**

**Query Parameters**:
- `userRef` (optional) - Filter by user reference

**Response**: `200 OK`
```json
[
  {
    "connectionId": "connection-uuid-123",
    "bankId": "bank-uuid-123",
    "status": "WAITING_OAUTH",
    "userRef": "user_sara_001",
    "nextStep": {
      "type": "OAUTH_REDIRECT",
      "details": { ... }
    }
  }
]
```

**Filtering**:
- If `userRef` provided → filters by tenant + userRef
- Otherwise → returns all connections for tenant

#### 3. Get Connection

**GET /v1/bank-connections/{connectionId}`**

**Response**: `200 OK` - Same structure as list response (single object)

**Tenant Isolation**: Automatically enforced via RLS (tenants can only see their own connections)

## Next Step Resolution

**File**: `src/main/java/com/asp/service/BankConnectionService.java`

The service dynamically computes `nextStep` based on bank's `connectionType`:

### Connection Types & Next Steps

#### 1. OAUTH
- **Status**: `WAITING_OAUTH`
- **Next Step**: `OAUTH_REDIRECT`
- **Details**: 
  ```json
  {
    "redirectUrl": "http://localhost:8080/v1/bank-connections/{connectionId}/oauth/start"
  }
  ```

#### 2. PDF
- **Status**: `WAITING_UPLOAD`
- **Next Step**: `UPLOAD_PDF`
- **Details**:
  ```json
  {
    "uploadUrl": "/v1/bank-connections/{connectionId}/documents"
  }
  ```

#### 3. SCRAPER
- **Status**: `PENDING`
- **Next Step**: `CREDENTIALS_INPUT`
- **Details**:
  ```json
  {
    "fields": ["username", "password", "otp"]
  }
  ```

#### 4. UNKNOWN
- **Status**: `PENDING`
- **Next Step**: `UNSUPPORTED`
- **Details**:
  ```json
  {
    "reason": "CONNECTION_TYPE_NOT_SUPPORTED"
  }
  ```

**Important**: `nextStep` is computed on-the-fly, not stored in database. This allows dynamic updates based on connection state.

## DTOs

### BankResponse
```java
{
  UUID id;
  String name;
  String slug;
  String country;
  ConnectionType connectionType;
  String iconUrl;
}
```

### BankConnectionResponse
```java
{
  UUID connectionId;
  UUID bankId;
  String status;
  String userRef;
  BankConnectionNextStep nextStep;
  Map<String, Object> metadata;
}
```

### BankConnectionNextStep
```java
{
  BankConnectionNextStepType type;  // OAUTH_REDIRECT, UPLOAD_PDF, CREDENTIALS_INPUT, UNSUPPORTED
  Map<String, Object> details;
}
```

## Database Schema

**Migration**: `V3__bank_directory_and_connection_flow.sql`

### Bank Table
```sql
CREATE TABLE bank (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(150) NOT NULL UNIQUE,
    country VARCHAR(50) NOT NULL,
    connection_type VARCHAR(30) NOT NULL CHECK (connection_type IN ('OAUTH', 'PDF', 'SCRAPER', 'UNKNOWN')),
    icon_url VARCHAR(300),
    status VARCHAR(30) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### Bank Connection Table
```sql
CREATE TABLE bank_connection (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    bank_id UUID NOT NULL REFERENCES bank(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING', 'WAITING_UPLOAD', 'WAITING_OAUTH', 'CONNECTED', 'FAILED')),
    step VARCHAR(50),
    user_ref VARCHAR NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### Row Level Security

**Migration**: `V4__add_user_ref_to_bank_connections.sql`

RLS enabled on `bank_connection`:
```sql
ALTER TABLE bank_connection ENABLE ROW LEVEL SECURITY;

CREATE POLICY bank_connection_tenant_isolation ON bank_connection
    USING (tenant_id = current_setting('asp.tenant_id', true)::uuid);
```

**Indexes**:
- `idx_bank_slug` - Fast bank lookup
- `idx_bank_connection_tenant_id` - Tenant filtering
- `idx_bank_connection_bank_id` - Bank filtering
- `idx_bank_connection_user_ref` - User reference filtering

## User Reference (userRef)

**Purpose**: Identifies the end-user within a tenant's context.

**Characteristics**:
- Tenant-provided opaque string
- Unique per tenant (not globally unique)
- Used to filter connections, ingestions, and future data
- No PII stored (just reference identifier)

**Example Values**:
- `user_sara_001`
- `customer_12345`
- `end_user_john_doe`

**Filtering**: Connections and ingestions can be filtered by `userRef` to show data for specific users.

## Service Layer

**File**: `src/main/java/com/asp/service/BankConnectionService.java`

**Methods**:
- `createConnection(UUID bankId, UUID tenantId, String userRef)` - Creates new connection
- `listConnections(UUID tenantId, String userRef)` - Lists connections (optional userRef filter)
- `getConnection(UUID connectionId, UUID tenantId)` - Gets single connection
- `determineNextStep(BankConnection, Bank)` - Computes next step (private helper)

**Configuration**:
- Injects `asp.platform.public-base-url` for OAuth redirect URLs
- Default: `http://localhost:8080`

## Future Enhancements

1. **OAuth Flow**: Implement actual OAuth redirect and callback handling
2. **PDF Upload**: Link to ingestion endpoint when ready
3. **Credential Input**: Create endpoint for scraper credential submission
4. **Connection Status Updates**: Webhooks or polling for status changes
5. **Connection Metadata**: Store OAuth tokens, credentials (encrypted), etc.

