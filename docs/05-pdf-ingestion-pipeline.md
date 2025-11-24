# PDF Ingestion Pipeline

## Overview

The PDF ingestion pipeline allows tenants to upload bank statement PDFs for processing. Files are stored locally (with abstraction for future S3 migration) and ingestion records track processing status.

## Architecture

### Storage Abstraction

**Interface**: `src/main/java/com/asp/storage/DocumentStorageService.java`

```java
public interface DocumentStorageService {
    String storeIngestionFile(UUID tenantId, UUID ingestionId, 
                              String originalFileName, InputStream content) 
        throws IOException;
}
```

**Implementations**:
1. **LocalDocumentStorageService** (current) - Stores files on local filesystem
2. **S3DocumentStorageService** (skeleton) - Future AWS S3 implementation

**Design**: Pluggable storage layer allows switching between local and cloud storage without changing business logic.

### Local Storage Structure

```
storage/
  tenants/
    {tenantId}/
      ingestions/
        {ingestionId}/
          original/
            {sanitizedFileName}
```

**Example**:
```
storage/tenants/550e8400-.../ingestions/7a7b5761-.../original/statement.pdf
```

**Features**:
- Automatic directory creation
- Filename sanitization (prevents path traversal)
- Relative path storage in database
- Configurable base path via `asp.storage.local-base-path`

## Database Schema

**Migration**: `V5__ingestion_table.sql`

### Ingestion Table

```sql
CREATE TABLE ingestion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    bank_connection_id UUID NOT NULL REFERENCES bank_connection(id) ON DELETE CASCADE,
    user_ref VARCHAR NOT NULL,
    status VARCHAR NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    source VARCHAR NOT NULL CHECK (source IN ('MANUAL_UPLOAD')),
    ingestion_type VARCHAR NOT NULL CHECK (ingestion_type IN ('PDF_STATEMENT')),
    original_file_name VARCHAR,
    file_size BIGINT,
    storage_path VARCHAR,
    error_code VARCHAR,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE
);
```

### Status Values

- **PENDING** - Uploaded, awaiting processing
- **PROCESSING** - Currently being processed
- **COMPLETED** - Successfully processed
- **FAILED** - Processing failed (check `error_code` and `error_message`)

### Row Level Security

RLS enabled for tenant isolation:
```sql
ALTER TABLE ingestion ENABLE ROW LEVEL SECURITY;

CREATE POLICY ingestion_tenant_isolation ON ingestion
    USING (tenant_id = current_setting('asp.tenant_id', true)::uuid);
```

**Indexes**:
- `idx_ingestion_tenant_id_user_ref` - Filter by tenant + userRef
- `idx_ingestion_bank_connection_id` - Filter by bank connection
- `idx_ingestion_status` - Find pending/completed ingestions
- `idx_ingestion_tenant_id` - List all for tenant

## REST API Endpoints

### 1. Upload PDF Statement

**POST /v1/ingestions**

**Content-Type**: `multipart/form-data`

**Form Fields**:
- `file` (File) - PDF file to upload
- `bankConnectionId` (String) - UUID of bank connection

**Headers**:
- `X-Api-Key` - Required for authentication

**Response**: `201 CREATED`
```json
{
  "id": "ingestion-uuid-123",
  "bankConnectionId": "connection-uuid-123",
  "userRef": "user_sara_001",
  "status": "PENDING",
  "source": "MANUAL_UPLOAD",
  "ingestionType": "PDF_STATEMENT",
  "originalFileName": "statement.pdf",
  "fileSize": 12345,
  "storagePath": "tenants/550e8400-.../ingestions/7a7b5761-.../original/statement.pdf",
  "createdAt": "2025-11-22T10:00:00Z",
  "updatedAt": "2025-11-22T10:00:00Z"
}
```

**Behavior**:
1. Validates file is not null/empty
2. Validates bank connection exists and belongs to tenant
3. Reads `userRef` from bank connection
4. Creates ingestion record with `PENDING` status
5. Stores file using `DocumentStorageService`
6. Updates ingestion with storage path
7. On storage failure: Marks as `FAILED` with error details

**Error Handling**:
- Empty file → `IllegalArgumentException` (400)
- Bank connection not found → `IllegalArgumentException` (400)
- Storage failure → Ingestion marked as `FAILED` (still returns 201, check status)

### 2. Get Ingestion

**GET /v1/ingestions/{id}`**

**Response**: `200 OK`
```json
{
  "id": "ingestion-uuid-123",
  "bankConnectionId": "connection-uuid-123",
  "userRef": "user_sara_001",
  "status": "PROCESSING",
  "source": "MANUAL_UPLOAD",
  "ingestionType": "PDF_STATEMENT",
  "originalFileName": "statement.pdf",
  "fileSize": 12345,
  "storagePath": "tenants/.../original/statement.pdf",
  "errorCode": null,
  "errorMessage": null,
  "createdAt": "2025-11-22T10:00:00Z",
  "updatedAt": "2025-11-22T10:05:00Z",
  "processedAt": null
}
```

### 3. List Ingestions

**GET /v1/ingestions?userRef={userRef}&bankConnectionId={connectionId}`**

**Query Parameters**:
- `userRef` (optional) - Filter by user reference
- `bankConnectionId` (optional) - Filter by bank connection

**Response**: `200 OK` - Array of ingestion objects

**Filtering Logic**:
1. If `userRef` provided → filter by tenant + userRef
2. Else if `bankConnectionId` provided → filter by tenant + bankConnectionId
3. Else → return all ingestions for tenant

**Example**:
```bash
# List all ingestions for tenant
GET /v1/ingestions

# List ingestions for specific user
GET /v1/ingestions?userRef=user_sara_001

# List ingestions for specific bank connection
GET /v1/ingestions?bankConnectionId=connection-uuid-123
```

## Service Layer

**File**: `src/main/java/com/asp/service/IngestionService.java`

**Methods**:

### createIngestionForPdf()
- Validates file and bank connection
- Creates ingestion entity
- Stores file via `DocumentStorageService`
- Handles storage errors gracefully
- Returns `IngestionResponse`

### getIngestion()
- Retrieves ingestion by ID (tenant-scoped)
- Throws `IllegalArgumentException` if not found

### listIngestions()
- Lists ingestions with optional filtering
- Supports `userRef` and `bankConnectionId` filters

### processPendingIngestions() (Skeleton)
- Placeholder for future processing pipeline
- Will process PENDING ingestions and extract data

## File Storage Service

**File**: `src/main/java/com/asp/storage/LocalDocumentStorageService.java`

**Features**:
- **Filename Sanitization**: Removes path traversal attempts, normalizes characters
- **Directory Creation**: Automatically creates nested directories
- **Path Normalization**: Returns relative paths with forward slashes
- **Error Handling**: Throws `IOException` for storage failures

**Configuration**:
```yaml
asp:
  storage:
    local-base-path: ${ASP_LOCAL_STORAGE_BASE_PATH:./storage}
```

**Filename Sanitization Rules**:
- Remove path separators (`/`, `\`)
- Remove parent directory references (`..`)
- Replace invalid characters with underscore
- Default to `unnamed.pdf` if filename empty
- Ensure `.pdf` extension if missing

## DTOs

### IngestionResponse
```java
{
  UUID id;
  UUID bankConnectionId;
  String userRef;
  String status;              // PENDING, PROCESSING, COMPLETED, FAILED
  String source;              // MANUAL_UPLOAD
  String ingestionType;       // PDF_STATEMENT
  String originalFileName;
  Long fileSize;
  String storagePath;
  String errorCode;
  String errorMessage;
  Instant createdAt;
  Instant updatedAt;
  Instant processedAt;
}
```

## Configuration

### Multipart File Upload

**File**: `src/main/resources/application.yml`

```yaml
spring:
  servlet:
    multipart:
      enabled: true
      max-file-size: 10MB
      max-request-size: 10MB
      file-size-threshold: 2KB
      location: ${java.io.tmpdir}
```

**Limits**:
- Maximum file size: 10MB
- Maximum request size: 10MB
- Files smaller than 2KB stored in memory, larger files on disk

### Controller Configuration

**File**: `src/main/java/com/asp/api/IngestionController.java`

- Accepts `multipart/form-data` content type
- Uses `@RequestPart` for file upload
- Uses `@RequestParam` for UUID form field
- Logs Content-Type and file metadata for debugging

## Processing Pipeline (Future)

**Workflow** (not yet implemented):
```
PENDING → PROCESSING → COMPLETED
                  ↓
                FAILED
```

**Future Steps**:
1. Worker picks up PENDING ingestions
2. Marks as PROCESSING
3. Extracts data from PDF (OCR, parsing)
4. Creates transaction records
5. Marks as COMPLETED or FAILED
6. Updates `processedAt` timestamp

## Error Handling

### Storage Errors

If file storage fails:
- Ingestion record still created
- Status set to `FAILED`
- `errorCode`: `STORAGE_ERROR`
- `errorMessage`: Exception message
- Allows debugging without losing request data

### Validation Errors

- **Null/empty file**: `IllegalArgumentException` → 400 Bad Request
- **Bank connection not found**: `IllegalArgumentException` → 400 Bad Request
- **Missing userRef**: `IllegalStateException` → 500 Internal Server Error

## Security

- **Authentication**: All endpoints require API key
- **Tenant Isolation**: RLS ensures tenants only see their own ingestions
- **File Validation**: Content-type warning for non-PDF files
- **Filename Sanitization**: Prevents path traversal attacks
- **Storage Path**: Not exposed in API response by default

## Example Usage

### Upload PDF via curl

```bash
curl -X POST http://localhost:8080/v1/ingestions \
  -H "X-Api-Key: asp_live_abc12345_secret" \
  -F "file=@/path/to/statement.pdf" \
  -F "bankConnectionId=connection-uuid-123"
```

### Upload PDF via Postman

1. Method: `POST`
2. URL: `http://localhost:8080/v1/ingestions`
3. Headers:
   - `X-Api-Key`: `asp_live_<prefix>_<secret>`
4. Body → form-data:
   - `file` (File): Select PDF file
   - `bankConnectionId` (Text): Enter UUID string

## Next Steps

1. **PDF Parsing**: Implement OCR and data extraction
2. **Processing Worker**: Create background job processor
3. **S3 Storage**: Implement cloud storage option
4. **File Download**: Add endpoint to retrieve stored files
5. **File Deletion**: Add cleanup endpoint
6. **Retry Mechanism**: Retry failed ingestions
7. **Webhooks**: Notify tenants on completion

