# ASP Platform - Backend Architecture Overview

**Purpose:** High-level architecture documentation for context understanding  
**Target:** AI agents implementing user-based session authentication  
**Last Updated:** December 2024

---

## 1. System Overview

**ASP Platform** is a **multi-tenant fintech backend** (Plaid-for-Morocco) that processes bank statement PDFs, normalizes financial data, and provides enriched transaction insights.

**Core Purpose:**
- PDF bank statement ingestion and parsing
- Transaction normalization and enrichment
- Multi-tenant data isolation
- RESTful API for frontend consumption

**Technology Stack:**
- **Framework:** Spring Boot 3.3 (Java 17)
- **Database:** PostgreSQL 15+ with Row-Level Security (RLS)
- **ORM:** Spring Data JPA / Hibernate 6.5
- **Security:** Spring Security (currently API key-based, stateless)
- **Build:** Gradle (Kotlin DSL)
- **Migrations:** Flyway

---

## 2. Architecture Layers

```
┌─────────────────────────────────────────────────────────┐
│                    API Layer (REST)                      │
│  /v1/accounts, /v1/ingestions, /v1/banks, etc.          │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│              Security & Authentication Layer             │
│  ApiKeyAuthenticationFilter → TenantContext              │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│                  Service Layer                           │
│  Business logic, orchestration, validation               │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│              Domain & Repository Layer                    │
│  JPA Entities, Spring Data Repositories                  │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│              Database Layer (PostgreSQL)                  │
│  Tables with RLS policies, Flyway migrations             │
└──────────────────────────────────────────────────────────┘
```

---

## 3. Current Authentication Architecture

### 3.1 Authentication Model: **API Key-Based (Stateless)**

**Current Flow:**
```
Client Request
    ↓
X-Api-Key Header: "asp_live_<prefix>_<secret>"
    ↓
ApiKeyAuthenticationFilter (Spring Security Filter)
    ├── Parse API key token
    ├── Validate via ApiKeyService
    ├── Set TenantContext (ThreadLocal)
    ├── Create UsernamePasswordAuthenticationToken
    └── Set SecurityContextHolder
    ↓
Controller (with tenant context)
    ↓
Response
```

**Key Components:**
- **`ApiKeyAuthenticationFilter`**: Custom Spring Security filter (extends `OncePerRequestFilter`)
- **`ApiKeyService`**: Validates API keys, checks tenant status
- **`ApiKeyToken`**: Parses API key format (`asp_live_<prefix>_<secret>`)
- **`TenantContext`**: ThreadLocal storage for tenant ID
- **`SecurityConfig`**: Spring Security configuration (stateless, no sessions)

**Current Security Configuration:**
- **Sessions:** `STATELESS` (no session cookies)
- **CSRF:** Disabled (stateless API)
- **Authentication:** API key in `X-Api-Key` header
- **Principal:** Tenant ID (UUID) stored as string in `Authentication.principal`
- **Authorities:** `ROLE_API_KEY` granted to all authenticated requests

---

## 4. Multi-Tenancy Architecture

### 4.1 Tenant Isolation Strategy

**Row-Level Security (RLS):**
- PostgreSQL RLS policies on all tenant-scoped tables
- Session variable `asp.tenant_id` set by `TenantTransactionInterceptor`
- Automatic query filtering at database level

**Application-Level Isolation:**
- `TenantContext` (ThreadLocal) stores tenant ID per request
- All service methods filter by tenant ID
- Repository queries include tenant ID in WHERE clauses

**Tenant Entity:**
- `Tenant` table with status (ACTIVE, SUSPENDED, etc.)
- Each tenant has multiple API keys
- Each tenant has isolated data (accounts, transactions, ingestions)

---

## 5. Core Components

### 5.1 API Controllers (`com.asp.api`)

**Public API Endpoints:**
- **`AccountsController`**: `/v1/accounts` - Account queries, transactions, enriched transactions
- **`IngestionController`**: `/v1/ingestions` - PDF upload, status tracking
- **`BankController`**: `/v1/banks` - Bank directory
- **`BankConnectionController`**: `/v1/bank-connections` - Bank connection management
- **`ApiKeyController`**: `/v1/api-keys` - API key CRUD operations
- **`PingController`**: `/v1/ping` - Health check

**Internal API Endpoints** (`com.asp.controller.internal`):
- **`IngestionProcessingController`**: `/internal/ingestions/{id}/process` - Trigger processing
- **`IngestionMetadataController`**: `/internal/ingestions/{id}` - Metadata for workers
- **`EnrichmentTestController`**: `/internal/test/run-enrichment/{id}` - Test enrichment
- **`CihDebugController`**: `/internal/debug/cih-detect` - Parser debugging

### 5.2 Service Layer (`com.asp.service`)

**Core Services:**
- **`AccountQueryService`**: Account and transaction queries with tenant isolation
- **`IngestionService`**: PDF upload, storage, ingestion creation
- **`ApiKeyManagementService`**: API key CRUD, validation
- **`BankConnectionService`**: Bank connection lifecycle management
- **`BankDirectoryService`**: Bank directory queries

**Processing Services** (`com.asp.service.processing`):
- **`IngestionProcessingFacade`**: Orchestrates ingestion processing
- **`DefaultIngestionProcessorService`**: Main processing pipeline
- **`TextExtractionService`**: PDF text extraction
- **`BankDetectionService`**: Bank identification from PDF content
- **`StatementParsingService`**: Statement parsing orchestration

**Storage Services** (`com.asp.service.storage`):
- **`S3StorageService`**: S3 upload/download abstraction
- **`S3UploadService`**: S3 upload operations
- **`S3DownloadService`**: S3 download operations

### 5.3 Enrichment Pipeline (`com.asp.platform.enrichment`)

**Enrichment Services:**
- **`EnrichmentService`**: Main enrichment orchestration
- **`CategoryClassifierService`**: Rule-based transaction categorization
- **`MerchantNormalizationService`**: Merchant name cleaning and normalization
- **`MerchantFuzzyMatcher`**: Fuzzy matching for merchant names
- **`MerchantDictionaryService`**: YAML-based merchant dictionary

**Enrichment Flow:**
```
NormalizedTransaction
    ↓
EnrichedTransactionMapper
    ├── CategoryClassifierService.classify()
    ├── MerchantNormalizationService.cleanMerchant()
    ├── MerchantNormalizationService.normalize()
    └── MerchantFuzzyMatcher.match() (if needed)
    ↓
EnrichedTransaction (with metadata, category, merchant, etc.)
```

### 5.4 Parsing Pipeline (`com.asp.parsing`)

**Parser Components:**
- **`ParserRegistry`**: Centralized parser registration
- **`ParserStrategy`**: Interface for bank-specific parsers
- **`DefaultBankDetector`**: Bank detection from PDF content
- **`DefaultTransactionWriter`**: Writes parsed data to database
- **`ParserErrorLogger`**: Error tracking and logging

**Supported Banks:**
- **CIH Bank**: Production-ready parser
- **Extensible**: Framework for adding more banks

---

## 6. Domain Model

### 6.1 Core Entities

**Tenant & Authentication:**
- **`Tenant`**: Multi-tenant organization (id, name, status, plan)
- **`ApiKey`**: API key credentials (prefix, keyHash, tenant, scopes, sandbox)

**Bank & Connections:**
- **`Bank`**: Bank directory (id, code, name, logoUrl, country, active)
- **`BankConnection`**: User's connection to a bank (id, bankId, tenantId, userRef, status)

**Ingestion:**
- **`Ingestion`**: PDF upload and processing job (id, bankConnectionId, status, source, fileSize, storagePath)

**Normalized Data:**
- **`NormalizedAccount`**: Bank account (accountId, accountNumber, iban, currency, balance, tenantId)
- **`NormalizedTransaction`**: Transaction (transactionId, date, amount, description, category, merchant, tenantId)
- **`NormalizedAccountSnapshot`**: Balance snapshot (date, balance, ingestionId)

**Enriched Data:**
- **`EnrichedTransaction`**: Enriched transaction (id, category, subcategory, merchantName, merchantNormalized, salary, recurring, direction, metadata)

### 6.2 Database Schema

**Key Tables:**
- `tenant` - Multi-tenant organizations
- `api_key` - API key credentials (BCrypt hashed secrets)
- `bank` - Bank directory (global, not tenant-scoped)
- `bank_connection` - User bank connections
- `ingestion` - PDF ingestion jobs
- `normalized_account` - Bank accounts
- `normalized_transaction` - Transactions
- `normalized_account_snapshot` - Balance snapshots
- `enriched_transaction` - Enriched transactions with metadata
- `parser_error` - Parser error logging

**All tenant-scoped tables have:**
- `tenant_id` foreign key
- RLS policies enabled
- UUID primary keys

---

## 7. Data Flow

### 7.1 Ingestion Flow

```
1. Client uploads PDF → POST /v1/ingestions
   ↓
2. IngestionService.createIngestionForPdf()
   ├── Save Ingestion entity (status: PENDING)
   ├── Upload PDF to S3/Local storage
   └── Publish SQS message (if async processing)
   ↓
3. Worker/Processor picks up ingestion
   ↓
4. DefaultIngestionProcessorService.processIngestion()
   ├── TextExtractionService.extractText() (PDFBox)
   ├── BankDetectionService.detectBank()
   ├── StatementParsingService.parseStatement()
   ├── TransactionWriter.saveParserResult()
   └── EnrichmentService.enrichIngestion()
   ↓
5. Ingestion status: PROCESSED
```

### 7.2 Query Flow

```
1. Client request → GET /v1/accounts/{id}/transactions
   ↓
2. ApiKeyAuthenticationFilter
   ├── Validates API key
   ├── Sets TenantContext
   └── Sets SecurityContext
   ↓
3. AccountsController.getAccountTransactions()
   ↓
4. AccountQueryService.getAccountTransactions()
   ├── Validates account belongs to tenant
   └── Queries NormalizedTransactionRepository
   ↓
5. Repository query (with tenant filter)
   ↓
6. Response with transactions
```

---

## 8. Security Architecture

### 8.1 Current Authentication (API Key)

**Stateless Design:**
- No server-side sessions
- API key validated on every request
- Tenant ID extracted from API key
- SecurityContext cleared after each request

**API Key Format:**
- `asp_live_<prefix>_<secret>` (live keys)
- `asp_sandbox_<prefix>_<secret>` (sandbox keys)
- Prefix: 8-character identifier
- Secret: 32-byte random (BCrypt hashed in database)

**Validation Process:**
1. Parse API key token
2. Lookup by prefix in `api_key` table
3. Verify secret using BCrypt `PasswordEncoder.matches()`
4. Check tenant status (must be ACTIVE)
5. Update `lastUsedAt` timestamp
6. Return tenant ID and scopes

### 8.2 Authorization

**Current Model:**
- All authenticated requests get `ROLE_API_KEY`
- No fine-grained permissions (all API keys have full access)
- Tenant isolation ensures data access control

**Protected Endpoints:**
- All `/v1/**` endpoints require authentication
- `/v1/ping` and `/actuator/health` are public
- `/internal/**` use token-based auth (separate from API keys)

---

## 9. Configuration

### 9.1 Security Configuration (`SecurityConfig`)

**Key Settings:**
- **Session Policy:** `STATELESS`
- **CSRF:** Disabled
- **Filter Order:** `ApiKeyAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`
- **Password Encoder:** `BCryptPasswordEncoder`

**Request Matchers:**
- `/actuator/health` → `permitAll()`
- `/v1/ping` → `permitAll()`
- `/internal/**` → `permitAll()` (uses internal token auth)
- `/v1/**` → `authenticated()`

### 9.2 Application Configuration (`application.yml`)

**Key Properties:**
- Database connection (PostgreSQL)
- JPA/Hibernate settings
- Flyway migrations
- Jackson JSON serialization
- Multipart file upload (10MB max)
- AWS S3/SQS configuration (env-based)

---

## 10. Key Design Patterns

### 10.1 Multi-Tenancy Pattern

**ThreadLocal Context:**
- `TenantContext` stores tenant ID per request thread
- Automatically cleared after request
- Used by all service methods for tenant filtering

**Row-Level Security:**
- PostgreSQL RLS policies enforce tenant isolation
- Session variable `asp.tenant_id` set per transaction
- Automatic filtering at database level

### 10.2 Repository Pattern

**Spring Data JPA:**
- Repository interfaces extend `JpaRepository<T, ID>`
- Custom queries using `@Query` annotations
- Tenant-aware query methods (e.g., `findAllByTenantId()`)

### 10.3 Service Layer Pattern

**Business Logic Separation:**
- Controllers handle HTTP concerns
- Services contain business logic
- Repositories handle data access
- DTOs for API contracts

---

## 11. API Structure

### 11.1 Public API (`/v1/*`)

**Endpoints:**
- `GET /v1/ping` - Health check (public)
- `GET /v1/banks` - List banks
- `POST /v1/bank-connections/{bankId}/connect` - Create connection
- `GET /v1/bank-connections` - List connections
- `POST /v1/ingestions` - Upload PDF
- `GET /v1/ingestions/{id}` - Get ingestion status
- `GET /v1/accounts` - List accounts
- `GET /v1/accounts/{id}` - Get account
- `GET /v1/accounts/{id}/transactions` - Get transactions
- `GET /v1/accounts/{id}/enriched-transactions` - Get enriched transactions
- `GET /v1/accounts/{id}/snapshots` - Get balance snapshots
- `POST /v1/api-keys` - Create API key
- `GET /v1/api-keys` - List API keys
- `DELETE /v1/api-keys/{id}` - Delete API key

**Authentication:** All require `X-Api-Key` header (except `/v1/ping`)

### 11.2 Internal API (`/internal/*`)

**Endpoints:**
- `POST /internal/ingestions/{id}/process` - Trigger processing
- `GET /internal/ingestions/{id}` - Get metadata (for workers)
- `POST /internal/test/run-enrichment/{id}` - Test enrichment
- `GET /internal/debug/cih-detect` - Parser debugging

**Authentication:** Internal token (`X-Internal-Token` header)

---

## 12. Current Authentication Limitations

### 12.1 What's Missing for User-Based Sessions

**Current State:**
- ✅ API key authentication (stateless)
- ✅ Tenant context management
- ✅ Multi-tenant isolation
- ❌ User entity/model
- ❌ User authentication (login/logout)
- ❌ Session management
- ❌ User-specific data (user-scoped within tenant)
- ❌ Password-based authentication
- ❌ JWT or session tokens for users

**What Needs to Be Added:**
1. **User Entity:** User model with email, password, roles
2. **User Authentication:** Login endpoint, password validation
3. **Session Management:** HTTP sessions or JWT tokens
4. **User Context:** User ID in addition to tenant ID
5. **User-Tenant Relationship:** Many-to-many or one-to-many
6. **Authorization:** User roles and permissions
7. **Password Management:** Registration, password reset

---

## 13. Integration Points

### 13.1 External Services

**AWS Services:**
- **S3:** PDF document storage
- **SQS:** Async ingestion processing (optional)
- **Secrets Manager:** Configuration secrets (optional)

**Local Development:**
- Local file storage (alternative to S3)
- LocalStack for AWS emulation

### 13.2 Worker Service (`asp-worker`)

**Separate Service:**
- Polls SQS for ingestion jobs
- Processes PDFs asynchronously
- Calls back to `asp-core` via HTTP for metadata

---

## 14. Key Files for Authentication Context

**Current Authentication:**
- `com.asp.security.ApiKeyAuthenticationFilter` - Main auth filter
- `com.asp.security.ApiKeyService` - API key validation
- `com.asp.security.ApiKeyToken` - Token parsing
- `com.asp.config.SecurityConfig` - Spring Security config
- `com.asp.tenant.TenantContext` - ThreadLocal tenant context

**Domain:**
- `com.asp.domain.Tenant` - Tenant entity
- `com.asp.domain.ApiKey` - API key entity

**Controllers:**
- `com.asp.api.ApiKeyController` - API key management
- `com.asp.api.accounts.AccountsController` - Example authenticated controller

---

## 15. Database Relationships

```
Tenant (1) ──< (N) ApiKey
Tenant (1) ──< (N) BankConnection
Tenant (1) ──< (N) Ingestion
Tenant (1) ──< (N) NormalizedAccount
Tenant (1) ──< (N) NormalizedTransaction
Tenant (1) ──< (N) EnrichedTransaction

Bank (1) ──< (N) BankConnection
BankConnection (1) ──< (N) Ingestion
Ingestion (1) ──< (N) NormalizedTransaction
NormalizedTransaction (1) ──< (1) EnrichedTransaction
```

---

## 16. Future Authentication Requirements

**To Add User-Based Sessions:**

1. **User Entity & Repository**
   - User table with email, password hash, tenant relationship
   - User roles/permissions

2. **Authentication Endpoints**
   - `POST /v1/auth/login` - User login
   - `POST /v1/auth/logout` - User logout
   - `POST /v1/auth/register` - User registration
   - `POST /v1/auth/refresh` - Session refresh (if JWT)

3. **Session Management**
   - HTTP sessions (Spring Session) OR JWT tokens
   - Session storage (Redis, database, or in-memory)
   - Session expiration and cleanup

4. **Security Configuration Updates**
   - Add session-based authentication filter
   - Configure session management
   - Update `SecurityConfig` to support both API keys and sessions

5. **User Context**
   - `UserContext` (similar to `TenantContext`)
   - Store user ID alongside tenant ID
   - Update all services to filter by user (if needed)

6. **Authorization**
   - Role-based access control (RBAC)
   - Permission checks in controllers/services
   - User-scoped data access (within tenant)

---

## 17. Important Notes for Implementation

**Current Architecture Strengths:**
- Clean separation of concerns
- Multi-tenant isolation already in place
- Stateless design (easy to scale)
- ThreadLocal context management pattern established

**Considerations for User Sessions:**
- Maintain backward compatibility with API keys
- Support both authentication methods (API key + user sessions)
- User data should be scoped within tenant
- Consider JWT vs HTTP sessions based on frontend needs
- Password hashing already available (`BCryptPasswordEncoder`)

**Integration Points:**
- `SecurityConfig` - Add session configuration
- `ApiKeyAuthenticationFilter` - Keep existing, add new filter
- `TenantContext` - Add `UserContext` alongside
- Controllers - Add user context extraction
- Services - Add user filtering (if user-scoped data needed)

---

**End of Architecture Overview**

