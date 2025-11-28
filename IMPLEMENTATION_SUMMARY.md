# ASP Platform - Implementation Summary

## Overview
ASP Platform is a multi-tenant fintech backend system (similar to Plaid) that processes bank statements, normalizes financial data, and provides enriched transaction insights. Built with Spring Boot 3.3, PostgreSQL, and JPA/Hibernate.

---

## Core Architecture

### Multi-Tenancy
- **Tenant Isolation**: Row-Level Security (RLS) via `TenantContext`
- **API Key Authentication**: Per-tenant API keys with secure token validation
- **Tenant Management**: Support for multiple tenants with different plans (FREE, PRO, ENTERPRISE)

### Security
- **API Key Authentication Filter**: Custom filter for stateless authentication
- **Spring Security Integration**: Protected endpoints with tenant context validation
- **Secure Storage**: Local and S3 document storage support

---

## 1. Bank Connection & Ingestion Pipeline

### Bank Directory
- **Bank Registry**: Support for multiple Moroccan banks (CIH, Attijari, BMCE)
- **Bank Detection**: Automatic bank identification from PDF content
- **Bank Connection Flow**: Multi-step connection process with status tracking

### Document Ingestion
- **PDF Upload**: Support for bank statement PDFs via REST API
- **Storage**: Documents stored with tenant isolation (`tenants/{tenantId}/ingestions/{ingestionId}/`)
- **Ingestion Status**: PENDING → PROCESSING → COMPLETED/FAILED workflow
- **Manual Processing**: Internal endpoint to trigger ingestion processing manually

### Text Extraction
- **PDFBox Integration**: Extracts raw text from PDF statements
- **Multi-Page Support**: Handles multi-page statements with page-by-page extraction
- **Error Handling**: Graceful handling of corrupted or unreadable PDFs

---

## 2. CIH Bank Statement Parser

### Parser Implementation
- **Real CIH Parser**: Production-ready parser replacing dummy implementation
- **Multi-Page Support**: Handles FIRST, MIDDLE, and LAST page types
- **Dual-Format Regex**: Supports both spaced and merged date formats from PDFBox
- **Transaction Extraction**: Regex-based parsing of transaction lines

### Parsing Features
- **Metadata Extraction**:
  - Currency detection (MAD)
  - Account number from RIB pattern
  - Opening balance date and amount
- **Transaction Parsing**:
  - Date extraction (operation date, value date)
  - Amount parsing (French format: `1 234,56`)
  - Debit/Credit detection based on keywords
  - Description extraction
- **Edge Cases**:
  - Ignores header/footer lines
  - Skips movement totals (`REPORT TOTAL MOUVEMENT`)
  - Handles duplicate transactions (no deduplication)
  - Year inference from opening balance date

### Debugging & Monitoring
- **Extensive Logging**: Line-by-line processing logs
- **Debug Endpoint**: `/internal/debug/cih-detect` for isolated parser testing
- **Parser Warnings**: Tracks skipped lines and parsing failures
- **Error Logging**: Stores parser errors in database for analysis

---

## 3. Data Normalization

### Normalized Data Model
- **NormalizedAccount**: Bank account information (account number, IBAN, currency, type)
- **NormalizedTransaction**: Standardized transaction data (date, amount, description, merchant, category)
- **NormalizedAccountSnapshot**: Historical balance snapshots per ingestion

### Data Persistence
- **JPA Repositories**: Spring Data JPA repositories with tenant-aware queries
- **Batch Processing**: Optimized batch inserts for large statement processing
- **Transaction Management**: Proper transaction boundaries for data consistency
- **Duplicate Handling**: Allows legitimate duplicate transactions (removed unique constraint)

### Database Migrations
- **Flyway Integration**: Versioned database migrations
- **Schema Evolution**: Support for schema changes without data loss
- **Constraint Management**: Removed deduplication constraints to allow duplicates

---

## 4. Public Read APIs

### Accounts API (`/v1/accounts`)
- **GET `/v1/accounts`**: List all accounts for tenant (optional `userRef` filter)
- **GET `/v1/accounts/{accountId}`**: Get single account with latest snapshot
- **GET `/v1/accounts/{accountId}/transactions`**: Paginated transaction list with filters:
  - Date range (`startDate`, `endDate`)
  - Category filter
  - Merchant filter
  - Pagination (`limit`, `offset`)
- **GET `/v1/accounts/{accountId}/snapshots`**: Historical balance snapshots

### Service Layer
- **AccountQueryService**: Business logic for account queries
- **Tenant Isolation**: All queries automatically filtered by tenant context
- **Error Handling**: Proper HTTP status codes (404, 400, 401)

### DTOs
- `AccountResponse`: Account details with metadata
- `TransactionResponse`: Transaction information
- `AccountSnapshotResponse`: Balance snapshot data
- `PaginatedTransactionsResponse`: Paginated transaction list with metadata

---

## 5. Enrichment Pipeline

### Enriched Transaction Model
- **EnrichedTransaction Entity**: Extended transaction data with:
  - Category and subcategory classification
  - Merchant normalization
  - Salary detection
  - Recurring transaction detection
  - Direction (INCOME/EXPENSE)
  - Absolute amount
  - JSON metadata field

### Category Classification
- **Rule-Based Classifier**: `CategoryClassifierService` with JSON rule definitions
- **Priority-Based Matching**: Rules applied by priority order
- **Confidence Levels**: HIGH, MEDIUM, LOW confidence scoring
- **Description Normalization**: Pre-processing for better matching

### Merchant Normalization
- **MerchantNormalizationService**: Cleans and normalizes merchant names
- **Noise Removal**: Strips amounts, dates, common noise tokens
- **Alias Mapping**: Maps variations to canonical merchant names
- **Normalization Rules**: YAML-based configuration (`merchant_rules.yml`)
- **Format Standardization**: Lowercase, accent removal, underscore separation

### Enrichment Flow
1. **Trigger**: Automatically runs after normalized transactions are saved
2. **Fetch**: Retrieves all normalized transactions for an ingestion
3. **Map**: Converts `NormalizedTransaction` → `EnrichedTransaction`
4. **Enrich**: Applies category classification and merchant normalization
5. **Persist**: Saves enriched transactions to database
6. **Manual Trigger**: Test endpoint `/internal/test/run-enrichment/{ingestionId}`

---

## 6. Internal Tools & Debugging

### Internal Controllers
- **IngestionProcessingController**: Manual ingestion processing trigger
- **CihDebugController**: CIH parser debugging endpoint
- **EnrichmentTestController**: Manual enrichment testing

### Monitoring
- **Parser Registry**: Centralized parser registration and discovery
- **Error Tracking**: Parser errors stored in database
- **Warning System**: Parser warnings for non-fatal issues
- **Logging**: Comprehensive logging at all levels

---

## 7. Database Schema

### Core Tables
- `tenant`: Multi-tenant organization data
- `api_key`: API key management with tenant association
- `bank`: Bank directory
- `bank_connection`: Bank connection status and metadata
- `ingestion`: Document ingestion tracking
- `normalized_account`: Normalized account data
- `normalized_transaction`: Normalized transaction data
- `normalized_account_snapshot`: Balance snapshots
- `enriched_transaction`: Enriched transaction data
- `parser_error`: Parser error logging

### Key Features
- **UUID Primary Keys**: All entities use UUID for distributed system compatibility
- **Tenant Isolation**: Foreign keys to tenant table for RLS
- **Timestamps**: Created/updated timestamps on all entities
- **Indexes**: Optimized indexes for common query patterns
- **JSONB Support**: Metadata stored as JSONB in PostgreSQL

---

## 8. Technology Stack

### Backend Framework
- **Spring Boot 3.3**: Core framework
- **Spring Data JPA**: Database access layer
- **Hibernate 6.5**: ORM implementation
- **PostgreSQL 15**: Primary database
- **Flyway**: Database migration tool

### Libraries
- **PDFBox**: PDF text extraction
- **SnakeYAML**: YAML configuration parsing
- **Lombok**: Code generation (getters, setters, builders)
- **Jackson**: JSON serialization/deserialization

### Build & Deployment
- **Gradle**: Build tool with Kotlin DSL
- **Docker**: Containerization support
- **Spring DevTools**: Development hot-reload

---

## 9. Key Features Summary

✅ **Multi-tenant architecture** with RLS and API key authentication  
✅ **Bank statement ingestion** via PDF upload  
✅ **CIH PDF parser** with multi-page support  
✅ **Data normalization** pipeline (accounts, transactions, snapshots)  
✅ **Public read APIs** for account and transaction queries  
✅ **Enrichment pipeline** (category classification, merchant normalization)  
✅ **Debugging tools** for parser and enrichment testing  
✅ **Error handling** and logging throughout  
✅ **Database migrations** with Flyway  
✅ **Manual processing** endpoints for testing  

---

## 10. Current Status

### Completed
- Core multi-tenant infrastructure
- Bank connection and ingestion flow
- CIH PDF parser (production-ready)
- Data normalization and persistence
- Public read APIs
- Enrichment pipeline (Part A & B)
- Merchant normalization engine
- Category classification system

### In Progress / Next Steps
- Additional bank parsers (Attijari, BMCE)
- Enhanced enrichment rules
- Recurring transaction detection
- Salary detection improvements
- Performance optimization for large statements

---

## API Endpoints Overview

### Public APIs (require API key)
- `GET /v1/accounts` - List accounts
- `GET /v1/accounts/{id}` - Get account details
- `GET /v1/accounts/{id}/transactions` - Get transactions
- `GET /v1/accounts/{id}/snapshots` - Get balance snapshots
- `POST /v1/ingestions` - Upload bank statement
- `GET /v1/ingestions/{id}` - Get ingestion status

### Internal APIs (require API key)
- `POST /internal/ingestions/process` - Trigger processing
- `GET /internal/debug/cih-detect` - CIH parser debug
- `POST /internal/test/run-enrichment/{id}` - Test enrichment

---

*Last Updated: November 2025*

