# ASP Platform Documentation

Welcome to the ASP Platform documentation. This directory contains comprehensive guides for all major features and components of the platform.

## Documentation Index

### 1. [Project Setup & Initialization](./01-project-setup.md)
- Technology stack
- Project structure
- Configuration files
- Docker support
- Environment variables
- Quick start guide

### 2. [Multi-Tenancy & API Key Authentication](./02-multi-tenancy-and-authentication.md)
- Tenant context (ThreadLocal)
- API key authentication filter
- API key token parsing
- Row Level Security (RLS)
- Security configuration
- Authentication flow

### 3. [API Key Management](./03-api-key-management.md)
- Create, list, and delete API keys
- API key format and structure
- Scopes and permissions
- Security considerations
- Audit logging

### 4. [Bank Directory & Connection Management](./04-bank-directory-and-connections.md)
- Bank directory endpoint
- Bank connection lifecycle
- Connection types (OAuth, PDF, Scraper)
- Next step resolution
- User reference (userRef) concept
- Database schema

### 5. [PDF Ingestion Pipeline](./05-pdf-ingestion-pipeline.md)
- File upload endpoints
- Storage abstraction layer
- Local file storage
- Ingestion status tracking
- Error handling
- Processing pipeline (future)

## Quick Links

- **Run Guide**: See `RUN.md` in project root for setup instructions
- **API Endpoints**: All endpoints documented in respective feature docs
- **Database Migrations**: Located in `src/main/resources/db/migration/`
- **Configuration**: See `src/main/resources/application.yml`

## Architecture Overview

### Multi-Tenant Architecture

```
┌─────────────┐
│   Tenant    │
│  (API Key)  │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────────┐
│   ApiKeyAuthenticationFilter        │
│   - Validates API Key               │
│   - Sets TenantContext              │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│   TenantTransactionInterceptor      │
│   - Sets PostgreSQL RLS variable    │
└──────┬──────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────┐
│   Controller → Service → Repository │
│   (All tenant-scoped via RLS)       │
└─────────────────────────────────────┘
```

### Data Flow: PDF Ingestion

```
Client (Postman/curl)
  │
  ├─ POST /v1/ingestions (multipart/form-data)
  │   ├─ file: PDF
  │   └─ bankConnectionId: UUID
  │
  ▼
ApiKeyAuthenticationFilter
  │
  ├─ Validates API Key
  ├─ Sets TenantContext
  └─ Sets PostgreSQL RLS variable
  │
  ▼
IngestionController
  │
  ├─ Validates tenant context
  └─ Calls IngestionService
  │
  ▼
IngestionService
  │
  ├─ Validates file & bank connection
  ├─ Creates Ingestion entity (PENDING)
  └─ Calls DocumentStorageService
  │
  ▼
LocalDocumentStorageService
  │
  ├─ Sanitizes filename
  ├─ Creates directory structure
  └─ Stores file: storage/tenants/{tenantId}/ingestions/{ingestionId}/original/{file}
  │
  ▼
IngestionService (cont.)
  │
  ├─ Updates ingestion with storage path
  └─ Returns IngestionResponse
```

## Key Concepts

### Tenant Isolation

All data is isolated by tenant using:
1. **Application Level**: `TenantContext` (ThreadLocal)
2. **Database Level**: Row Level Security (RLS) policies

### API Key Authentication

- **Format**: `asp_live_<prefix>_<secret>` or `asp_sandbox_<prefix>_<secret>`
- **Header**: `X-Api-Key`
- **Validation**: BCrypt hashing, tenant status check
- **Scopes**: Fine-grained permissions (read, write)

### User Reference (userRef)

- Tenant-provided identifier for end-users
- Used to filter data within tenant context
- No PII stored (just reference string)
- Example: `user_sara_001`, `customer_12345`

### Connection Types

1. **OAUTH** - OAuth-based bank connection (future)
2. **PDF** - PDF statement upload
3. **SCRAPER** - Credential-based scraping (future)
4. **UNKNOWN** - Connection method not defined

### Ingestion Statuses

1. **PENDING** - Uploaded, awaiting processing
2. **PROCESSING** - Currently being processed
3. **COMPLETED** - Successfully processed
4. **FAILED** - Processing failed (check error fields)

## Database Migrations

All migrations are in `src/main/resources/db/migration/`:

- `V1__tenant_and_api_key_tables.sql` - Core tenant and API key tables
- `V2__enable_rls_and_policies.sql` - Row Level Security setup
- `V3__bank_directory_and_connection_flow.sql` - Bank directory and connections
- `V4__add_user_ref_to_bank_connections.sql` - User reference support
- `V5__ingestion_table.sql` - PDF ingestion pipeline

## Security Best Practices

1. **API Keys**: Stored as BCrypt hashes, never in plaintext
2. **Tenant Isolation**: Enforced at both application and database levels
3. **File Uploads**: Filename sanitization prevents path traversal
4. **Stateless API**: No session cookies, all authentication via API key header
5. **RLS Policies**: Database-level isolation as defense in depth

## Configuration Reference

### Environment Variables

```bash
# Database
DATASOURCE_URL=jdbc:postgresql://localhost:5432/asp_platform
DATASOURCE_USERNAME=asp
DATASOURCE_PASSWORD=asp

# Server
SERVER_PORT=8080

# Platform
ASP_PUBLIC_BASE_URL=http://localhost:8080
ASP_LOCAL_STORAGE_BASE_PATH=./storage
```

### Multipart Upload Limits

- Max file size: 10MB
- Max request size: 10MB
- Threshold: 2KB (smaller files in memory, larger on disk)

## API Endpoints Summary

### Authentication Required (All `/v1/**`)

- `POST /v1/api-keys` - Create API key
- `GET /v1/api-keys` - List API keys
- `DELETE /v1/api-keys/{id}` - Delete API key
- `GET /v1/banks` - List banks
- `POST /v1/bank-connections/{bankId}/connect` - Create connection
- `GET /v1/bank-connections` - List connections
- `GET /v1/bank-connections/{id}` - Get connection
- `POST /v1/ingestions` - Upload PDF
- `GET /v1/ingestions/{id}` - Get ingestion
- `GET /v1/ingestions` - List ingestions

### Public Endpoints

- `GET /actuator/health` - Health check
- `GET /v1/ping` - Test endpoint (returns tenant ID)

## Contributing

When adding new features:
1. Update relevant documentation in `docs/`
2. Add database migrations with descriptive names
3. Include unit tests
4. Update changelog entries
5. Follow existing code patterns and architecture

## Support

For issues or questions:
1. Check relevant documentation in this directory
2. Review `RUN.md` for setup help
3. Check application logs for errors
4. Verify database migrations are applied

---

**Last Updated**: 2025-11-22

