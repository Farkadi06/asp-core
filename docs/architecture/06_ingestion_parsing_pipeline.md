# Ingestion Parsing Pipeline Architecture

## Overview

This document describes the technical architecture for the PDF ingestion parsing pipeline (Step 6), which processes uploaded PDF bank statements to extract, normalize, and store transaction data.

**Status**: Architecture Design (Implementation: Pending)

---

## 1. High-Level Pipeline Overview

### Pipeline Flow

```
┌─────────────────┐
│  PDF Upload     │
│  (Ingestion API)│
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  Ingestion Record Created           │
│  Status: PENDING                    │
│  Storage: Local/S3                  │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  IngestionProcessor (Worker)        │
│  - Picks PENDING ingestions         │
│  - Marks as PROCESSING              │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  TextExtractor                      │
│  - Loads PDF file from storage      │
│  - Extracts raw text content        │
│  - Handles PDF parsing errors       │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  BankDetector                       │
│  - Analyzes text patterns           │
│  - Identifies bank (CIH, Attijari,  │
│    BMCE, etc.)                      │
│  - Returns bank identifier          │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  ParserRegistry                     │
│  - Looks up parser by bank          │
│  - Returns ParserStrategy instance  │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  ParserStrategy (Bank-specific)     │
│  - Parses bank-specific format      │
│  - Extracts accounts, transactions, │
│    balances, dates                  │
│  - Returns ParserResult             │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  Data Normalization                 │
│  - Converts to NormalizedTransaction│
│  - Standardizes dates, amounts,     │
│    account references               │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  TransactionWriter                  │
│  - Saves accounts → account table   │
│  - Saves snapshots → account_       │
│    snapshot table                   │
│  - Saves transactions → transaction │
│    table                            │
│  - Links to ingestion & tenant      │
└────────┬────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────┐
│  Status Update                      │
│  - Marks ingestion as PROCESSED     │
│  - Sets processedAt timestamp       │
│  - Or marks as FAILED with error    │
└─────────────────────────────────────┘
```

### Sequence Diagram

```
Client              API              Storage           Processor        Parser          Writer
  │                 │                  │                  │                │               │
  │─POST /ingestions│                  │                  │                │               │
  │────────────────>│                  │                  │                │               │
  │                 │─Save Ingestion   │                  │                │               │
  │                 │─────────────────>│                  │                │               │
  │                 │─Store File       │                  │                │               │
  │                 │─────────────────>│                  │                │               │
  │                 │<─────────────────│                  │                │               │
  │<─201 CREATED────│                  │                  │                │               │
  │                 │                  │                  │                │               │
  │                 │                  │                  │─Pick PENDING───│               │
  │                 │                  │                  │<───────────────│               │
  │                 │                  │                  │─Mark PROCESSING│               │
  │                 │                  │                  │────────────────>│               │
  │                 │                  │                  │                │               │
  │                 │                  │<─Load File───────│                │               │
  │                 │                  │──────────────────│                │               │
  │                 │                  │                  │─Extract Text───│               │
  │                 │                  │<─────────────────│                │               │
  │                 │                  │                  │                │               │
  │                 │                  │                  │─Detect Bank────│               │
  │                 │                  │                  │────────────────>│               │
  │                 │                  │                  │<─Bank ID───────│               │
  │                 │                  │                  │                │               │
  │                 │                  │                  │─Get Parser─────│               │
  │                 │                  │                  │────────────────>│               │
  │                 │                  │                  │                │─Parse─────────│
  │                 │                  │                  │                │<─Result───────│
  │                 │                  │                  │                │               │
  │                 │                  │                  │─Normalize──────│               │
  │                 │                  │                  │────────────────>│               │
  │                 │                  │                  │                │               │
  │                 │                  │                  │─Save Data──────│               │
  │                 │                  │                  │────────────────────────────────>│
  │                 │                  │                  │<────────────────────────────────│
  │                 │                  │                  │─Mark PROCESSED─│               │
  │                 │                  │                  │────────────────>│               │
```

---

## 2. Components & Responsibilities

### 2.1 IngestionProcessor (Worker)

**Responsibility**: Orchestrates the entire parsing pipeline.

**Key Functions**:
- Polls for PENDING ingestions (or receives async notifications)
- Updates ingestion status to PROCESSING
- Coordinates execution of all pipeline stages
- Handles errors and retries
- Updates final status (PROCESSED/FAILED)
- Manages transaction boundaries

**Interfaces**:
- `IngestionProcessorService` - Main service interface

**Properties**:
- Polling interval (configurable)
- Batch size (how many to process at once)
- Max retry attempts
- Timeout handling

---

### 2.2 TextExtractor

**Responsibility**: Extracts raw text content from PDF files.

**Key Functions**:
- Loads PDF from storage (local or S3)
- Converts PDF to plain text
- Handles encrypted/corrupted PDFs
- Returns raw text for analysis
- Provides text metadata (page count, encoding)

**Interfaces**:
- `PdfTextExtractor` - Main extraction interface

**Error Handling**:
- PDF parsing errors → Recoverable (mark ingestion as FAILED)
- Corrupted files → Non-recoverable
- Encrypted PDFs → Non-recoverable (user must provide password)

**Future Extensions**:
- OCR fallback for scanned PDFs
- Multi-page support
- Table structure detection

---

### 2.3 BankDetector

**Responsibility**: Identifies which bank issued the statement.

**Key Functions**:
- Analyzes extracted text for bank-specific patterns
- Checks bank logos, headers, footers
- Uses regex patterns and keyword matching
- Returns bank identifier (slug or enum)
- Handles unknown banks gracefully

**Detection Methods**:
1. **Text Pattern Matching**: Bank name, keywords, formats
2. **Header/Footer Analysis**: Bank logos, contact info
3. **Account Number Format**: Bank-specific patterns
4. **Statement Layout**: Document structure analysis

**Returns**:
- Bank identifier (String slug or Bank entity reference)
- Confidence level (HIGH, MEDIUM, LOW)
- Detection metadata (patterns matched)

**Fallback**:
- If unknown → Uses bank from `bank_connection` entity
- If detection fails but connection exists → Use connection's bank

---

### 2.4 ParserStrategy Interface

**Responsibility**: Defines contract for bank-specific parsers.

**Methods**:
```java
interface ParserStrategy {
    ParserResult parse(String extractedText, BankDetectionResult detection);
    boolean canHandle(String bankIdentifier);
    String getParserName();
    ParserCapabilities getCapabilities();
}
```

**Key Functions**:
- Parses bank-specific statement format
- Extracts structured data (accounts, transactions, balances)
- Returns normalized result object
- Handles format variations within same bank

**Implementation Strategy**:
- One parser per bank (CIH, Attijari, BMCE, etc.)
- Parser selection via registry pattern
- Pluggable architecture for adding new banks

---

### 2.5 ParserRegistry

**Responsibility**: Manages and provides access to bank parsers.

**Key Functions**:
- Registers parser strategies by bank identifier
- Returns appropriate parser for bank
- Handles parser not found scenarios
- Provides parser capabilities metadata

**Registry Pattern**:
- Map<String, ParserStrategy> by bank slug
- Lazy loading of parsers
- Factory method for parser creation

**Error Handling**:
- Unknown bank → Returns null, triggers FAILED status
- Parser not available → Logs error, marks ingestion as FAILED

---

### 2.6 NormalizedTransaction Model (Internal)

**Responsibility**: Standardized internal representation of transaction data.

**Structure**:
```java
class NormalizedTransaction {
    String accountNumber;        // Standardized account identifier
    String accountType;          // CHECKING, SAVINGS, etc.
    LocalDate transactionDate;   // ISO date
    LocalDate valueDate;         // Value date (if different)
    BigDecimal amount;           // Positive for credit, negative for debit
    String currency;             // MAD, USD, EUR
    String description;          // Transaction description
    String reference;            // Bank transaction reference
    String category;             // Optional: transaction category
    BigDecimal balance;          // Account balance after transaction
}
```

**Purpose**:
- Standardizes data from different bank formats
- Enables uniform database storage
- Simplifies downstream processing

---

### 2.7 TransactionWriter (DB Layer)

**Responsibility**: Persists extracted data to database.

**Key Functions**:
- Saves account information to `account` table
- Creates account snapshots in `account_snapshot` table
- Inserts transactions into `transaction` table
- Maintains referential integrity
- Handles duplicate detection (idempotency)
- Links all data to ingestion and tenant

**Transaction Management**:
- Wraps all writes in database transaction
- Rolls back on failure
- Ensures atomicity of data saves

**Idempotency**:
- Checks for existing transactions by reference + date
- Skips duplicates if reprocessing same ingestion
- Updates existing records if needed

---

### 2.8 ErrorHandler / ErrorLog

**Responsibility**: Captures and stores processing errors.

**Key Functions**:
- Logs errors to `parser_error` table
- Categorizes errors (PDF, detection, parsing, storage)
- Stores error context and stack traces
- Links errors to ingestion record
- Provides error codes for categorization

**Error Categories**:
1. **PDF_ERROR**: File reading/parsing issues
2. **DETECTION_ERROR**: Bank detection failures
3. **PARSING_ERROR**: Statement parsing failures
4. **STORAGE_ERROR**: Database write failures
5. **UNKNOWN_FORMAT**: Unsupported bank format

**Error Record Structure**:
- Error code
- Error message
- Stack trace (if applicable)
- Context data (JSON)
- Ingestion reference
- Timestamp

---

### 2.9 Retry Mechanism

**Responsibility**: Handles transient failures with automatic retries.

**Retry Strategy**:
- **Max Attempts**: 3 (configurable)
- **Backoff**: Exponential (1s, 2s, 4s)
- **Retryable Errors**: Database connection, temporary file access
- **Non-Retryable Errors**: Invalid PDF, unsupported format, parsing errors

**Implementation**:
- Retry counter in ingestion entity or separate retry log
- Marks ingestion for retry with delay
- Resets retry count on success

**Future Enhancement**:
- Dead-letter queue for failed ingestions
- Manual retry trigger via API
- Scheduled retry for transient errors

---

### 2.10 Idempotency Rules

**Responsibility**: Prevents duplicate processing and data duplication.

**Idempotency Keys**:
- **Ingestion Level**: Ingestion ID (can be reprocessed if needed)
- **Transaction Level**: Bank reference + transaction date + amount
- **Account Level**: Account number + tenant + userRef

**Duplicate Detection**:
- Check existing transactions before insert
- Skip if exact match found
- Update if data changed (reconciliation scenario)

**Reprocessing**:
- Allows re-running same ingestion (updates existing data)
- Tracks processing attempts
- Prevents infinite loops

---

### 2.11 Status Transitions

**Responsibility**: Defines valid state transitions and guards.

**State Machine**:
```
PENDING → PROCESSING → PARSED → SAVING → PROCESSED
  │          │            │         │
  │          │            │         └─── FAILED
  │          │            └───────────── FAILED
  │          └────────────────────────── FAILED
  └────────────────────────────────────── FAILED (pre-processing)
```

**Transition Rules**:
- Only one transition at a time (locked)
- Status changes are atomic
- Failed states can be retried (back to PENDING)
- PROCESSED is terminal (cannot revert)

---

### 2.12 Directory Structure for Storage

**Current Structure** (Local):
```
storage/
  tenants/
    {tenantId}/
      ingestions/
        {ingestionId}/
          original/
            {sanitizedFileName}        # Original uploaded PDF
          extracted/
            text.txt                   # Extracted text (optional cache)
            metadata.json              # Extraction metadata
          processed/
            parsed_data.json           # Parser output (debug)
```

**Future Structure** (S3):
- Same logical structure
- S3 keys instead of file paths
- Lifecycle policies for cleanup

---

## 3. Class Structure (Interfaces & Classes)

### 3.1 Core Interfaces

#### PdfTextExtractor
```java
interface PdfTextExtractor {
    TextExtractionResult extract(UUID tenantId, UUID ingestionId, String storagePath);
}

class TextExtractionResult {
    String text;
    int pageCount;
    String encoding;
    Map<String, Object> metadata;
    boolean isScanned;  // For OCR detection
}
```

#### ParserStrategy
```java
interface ParserStrategy {
    ParserResult parse(String extractedText, BankDetectionResult detection, IngestionContext context);
    boolean canHandle(String bankIdentifier);
    String getParserName();
    ParserCapabilities getCapabilities();
}

enum ParserCapabilities {
    ACCOUNT_EXTRACTION,
    TRANSACTION_EXTRACTION,
    BALANCE_EXTRACTION,
    DATE_RANGE_EXTRACTION,
    MULTI_ACCOUNT_SUPPORT
}
```

#### BankDetector
```java
interface BankDetector {
    BankDetectionResult detect(String extractedText, UUID bankConnectionId);
}

class BankDetectionResult {
    String bankIdentifier;  // Bank slug
    DetectionConfidence confidence;
    Map<String, Object> metadata;
    List<String> matchedPatterns;
}

enum DetectionConfidence {
    HIGH,    // >90% certainty
    MEDIUM,  // 50-90% certainty
    LOW      // <50% certainty
}
```

---

### 3.2 Parser Implementations

#### CihPdfParser
**Implements**: `ParserStrategy`

**Responsibilities**:
- Parses CIH (Crédit Immobilier et Hôtelier) PDF statements
- Handles CIH-specific format variations
- Extracts CIH account structures

**Format Characteristics**:
- Specific header/footer patterns
- Account number format
- Transaction table structure
- Date format (DD/MM/YYYY)

---

#### AttijariPdfParser
**Implements**: `ParserStrategy`

**Responsibilities**:
- Parses Attijariwafa Bank PDF statements
- Handles Attijari-specific layouts
- Extracts multi-account statements

**Format Characteristics**:
- Bank logo and branding
- Account summary section
- Detailed transaction listings
- Multi-currency support

---

#### BmcePdfParser
**Implements**: `ParserStrategy`

**Responsibilities**:
- Parses BMCE (Banque Marocaine du Commerce Extérieur) PDFs
- Handles BMCE statement format
- Extracts BMCE-specific fields

**Format Characteristics**:
- BMCE header structure
- Account information layout
- Transaction detail format

---

### 3.3 Data Models

#### ParserResult
```java
class ParserResult {
    List<NormalizedAccount> accounts;
    List<NormalizedTransaction> transactions;
    List<NormalizedAccountSnapshot> snapshots;
    DateRange statementPeriod;
    Map<String, Object> metadata;
    List<ParserWarning> warnings;
    boolean isComplete;  // All data extracted successfully
}
```

#### NormalizedTransaction
```java
class NormalizedTransaction {
    String accountNumber;
    String accountType;           // CHECKING, SAVINGS, etc.
    LocalDate transactionDate;
    LocalDate valueDate;          // Optional
    BigDecimal amount;            // Signed: positive = credit, negative = debit
    String currency;              // MAD, USD, EUR
    String description;
    String bankReference;         // Bank's transaction ID
    String category;              // Optional classification
    BigDecimal balanceAfter;      // Account balance after this transaction
    TransactionType type;         // DEBIT, CREDIT, TRANSFER
}
```

#### NormalizedAccountSnapshot
```java
class NormalizedAccountSnapshot {
    String accountNumber;
    String accountType;
    LocalDate snapshotDate;       // Statement date
    BigDecimal openingBalance;
    BigDecimal closingBalance;
    String currency;
    BigDecimal totalDebits;
    BigDecimal totalCredits;
    int transactionCount;
}
```

#### NormalizedAccount
```java
class NormalizedAccount {
    String accountNumber;
    String accountType;
    String accountName;           // Account holder name
    String currency;
    String bankIdentifier;        // Which bank
}
```

---

### 3.4 Service Classes

#### IngestionProcessorService
**Main orchestration service**

```java
interface IngestionProcessorService {
    void processPendingIngestions();
    void processIngestion(UUID ingestionId);
    void retryFailedIngestion(UUID ingestionId);
}
```

**Responsibilities**:
- Coordinates all pipeline stages
- Manages status transitions
- Handles errors and retries
- Updates ingestion status

---

#### ParserRegistry
**Parser lookup and management**

```java
interface ParserRegistry {
    ParserStrategy getParser(String bankIdentifier);
    void registerParser(String bankIdentifier, ParserStrategy parser);
    List<String> getSupportedBanks();
    boolean isSupported(String bankIdentifier);
}
```

**Implementation**:
- Spring Bean with @PostConstruct initialization
- Registers all available parsers
- Provides fallback for unknown banks

---

#### TransactionWriter
**Database persistence layer**

```java
interface TransactionWriter {
    void saveParserResult(ParserResult result, IngestionContext context);
    void saveAccount(NormalizedAccount account, IngestionContext context);
    void saveTransactions(List<NormalizedTransaction> transactions, IngestionContext context);
    void saveSnapshots(List<NormalizedAccountSnapshot> snapshots, IngestionContext context);
}
```

**Responsibilities**:
- Atomic saves within transaction
- Duplicate detection
- Referential integrity
- Error handling and rollback

---

### 3.5 Error Models

#### ParserError
```java
class ParserError {
    UUID id;
    UUID ingestionId;
    ErrorCategory category;
    String errorCode;
    String errorMessage;
    String stackTrace;
    Map<String, Object> context;  // JSON
    LocalDateTime occurredAt;
}

enum ErrorCategory {
    PDF_ERROR,
    DETECTION_ERROR,
    PARSING_ERROR,
    STORAGE_ERROR,
    UNKNOWN_FORMAT,
    VALIDATION_ERROR
}
```

---

## 4. Database Architecture (Preview)

### 4.1 Account Table

**Purpose**: Stores account information extracted from statements.

```sql
CREATE TABLE account (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    user_ref VARCHAR NOT NULL,
    bank_connection_id UUID NOT NULL REFERENCES bank_connection(id),
    account_number VARCHAR NOT NULL,
    account_type VARCHAR NOT NULL,  -- CHECKING, SAVINGS, etc.
    account_name VARCHAR,
    currency VARCHAR(3) NOT NULL DEFAULT 'MAD',
    bank_identifier VARCHAR NOT NULL,  -- Bank slug
    first_seen_at TIMESTAMP WITH TIME ZONE,
    last_seen_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(tenant_id, user_ref, account_number)
);

CREATE INDEX idx_account_tenant_user_ref ON account(tenant_id, user_ref);
CREATE INDEX idx_account_bank_connection ON account(bank_connection_id);
```

**RLS Policy**: Tenant-based isolation

---

### 4.2 Account Snapshot Table

**Purpose**: Stores account balance snapshots from each statement.

```sql
CREATE TABLE account_snapshot (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    ingestion_id UUID NOT NULL REFERENCES ingestion(id) ON DELETE CASCADE,
    snapshot_date DATE NOT NULL,
    opening_balance DECIMAL(15, 2) NOT NULL,
    closing_balance DECIMAL(15, 2) NOT NULL,
    total_debits DECIMAL(15, 2),
    total_credits DECIMAL(15, 2),
    transaction_count INTEGER,
    currency VARCHAR(3) NOT NULL DEFAULT 'MAD',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(account_id, ingestion_id)
);

CREATE INDEX idx_account_snapshot_account ON account_snapshot(account_id);
CREATE INDEX idx_account_snapshot_ingestion ON account_snapshot(ingestion_id);
CREATE INDEX idx_account_snapshot_date ON account_snapshot(snapshot_date);
```

**Purpose**: Track account balances over time, enable reconciliation.

---

### 4.3 Transaction Table

**Purpose**: Stores individual transactions extracted from statements.

```sql
CREATE TABLE transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES account(id) ON DELETE CASCADE,
    ingestion_id UUID NOT NULL REFERENCES ingestion(id) ON DELETE CASCADE,
    transaction_date DATE NOT NULL,
    value_date DATE,
    amount DECIMAL(15, 2) NOT NULL,  -- Signed: positive = credit, negative = debit
    currency VARCHAR(3) NOT NULL DEFAULT 'MAD',
    description TEXT,
    bank_reference VARCHAR,  -- Bank's transaction ID
    category VARCHAR,
    balance_after DECIMAL(15, 2),  -- Account balance after this transaction
    transaction_type VARCHAR NOT NULL,  -- DEBIT, CREDIT, TRANSFER
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(account_id, bank_reference, transaction_date, amount)  -- Idempotency
);

CREATE INDEX idx_transaction_account ON transaction(account_id);
CREATE INDEX idx_transaction_ingestion ON transaction(ingestion_id);
CREATE INDEX idx_transaction_date ON transaction(transaction_date);
CREATE INDEX idx_transaction_account_date ON transaction(account_id, transaction_date);
```

**Idempotency**: Unique constraint on (account_id, bank_reference, transaction_date, amount) prevents duplicates.

---

### 4.4 Parser Error Table

**Purpose**: Stores parsing errors for debugging and monitoring.

```sql
CREATE TABLE parser_error (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ingestion_id UUID NOT NULL REFERENCES ingestion(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    error_category VARCHAR NOT NULL,  -- PDF_ERROR, DETECTION_ERROR, etc.
    error_code VARCHAR NOT NULL,
    error_message TEXT NOT NULL,
    stack_trace TEXT,
    context JSONB,  -- Additional error context
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_parser_error_ingestion ON parser_error(ingestion_id);
CREATE INDEX idx_parser_error_tenant ON parser_error(tenant_id);
CREATE INDEX idx_parser_error_category ON parser_error(error_category);
CREATE INDEX idx_parser_error_occurred_at ON parser_error(occurred_at);
```

**RLS Policy**: Tenant-based isolation

---

### 4.5 Relationship Diagram

```
tenant (1) ──────< (N) account
  │                      │
  │                      │
  │                  (1) ────< (N) transaction
  │                      │
  │                      │
  │                  (1) ────< (N) account_snapshot
  │
  │
  ├───────< (N) bank_connection (1) ────────< (N) account
  │
  │
  └───────< (N) ingestion (1) ────────< (N) transaction
                │
                │
                ├───────< (N) account_snapshot
                │
                └───────< (N) parser_error
```

**Key Relationships**:
- `account` → `tenant` + `user_ref` + `account_number` (unique per tenant/user)
- `transaction` → `account` + `ingestion` (linked to both)
- `account_snapshot` → `account` + `ingestion` (one snapshot per statement)
- `parser_error` → `ingestion` + `tenant` (errors linked to ingestion)

---

## 5. Status Machine

### 5.1 Status Flow

```
┌─────────┐
│ PENDING │  (Initial state after upload)
└────┬────┘
     │
     │ Processor picks up
     ▼
┌─────────────┐
│ PROCESSING  │  (Worker processing)
└────┬────┬───┘
     │    │
     │    │ Text extraction successful
     │    ▼
     │ ┌─────────┐
     │ │ PARSED  │  (Text extracted, bank detected, parsed)
     │ └────┬────┘
     │      │
     │      │ Data normalization successful
     │      ▼
     │ ┌──────────┐
     │ │ SAVING   │  (Writing to database)
     │ └────┬─────┘
     │      │
     │      │ Save successful
     │      ▼
     │ ┌─────────────┐
     │ │ PROCESSED   │  (Final success state)
     │ └─────────────┘
     │
     └───> Any failure at any stage
           ▼
      ┌────────┐
      │ FAILED │  (Error occurred)
      └────┬───┘
           │
           │ Retry (if recoverable)
           └───────> Back to PENDING
```

### 5.2 Status Definitions

#### PENDING
- **Initial State**: After PDF upload, before processing
- **Transitions To**: PROCESSING (when worker picks up)
- **Can Also Transition To**: FAILED (if pre-processing validation fails)

#### PROCESSING
- **State**: Worker is actively processing the ingestion
- **Transitions To**: 
  - PARSED (successful text extraction + parsing)
  - FAILED (any error during processing)
- **Lock**: Ingestion record locked to prevent concurrent processing

#### PARSED
- **State**: Text extracted, bank detected, data parsed successfully
- **Transitions To**:
  - SAVING (ready to write to database)
  - FAILED (normalization or validation error)

#### SAVING
- **State**: Writing extracted data to database
- **Transitions To**:
  - PROCESSED (all data saved successfully)
  - FAILED (database error during save)

#### PROCESSED
- **Final Success State**: All stages completed successfully
- **No Further Transitions**: Terminal state
- **Properties**: `processedAt` timestamp set

#### FAILED
- **Error State**: Processing failed at some stage
- **Properties**: `errorCode`, `errorMessage` populated
- **Transitions To**: PENDING (if retry enabled for this error type)
- **Causes**: See section 5.3

---

### 5.3 Failure Causes

#### PDF Errors (Category: PDF_ERROR)
- **Corrupted PDF**: File is unreadable or corrupted
  - Error Code: `PDF_CORRUPTED`
  - Recoverable: No (file must be re-uploaded)
  
- **Encrypted PDF**: PDF requires password
  - Error Code: `PDF_ENCRYPTED`
  - Recoverable: No (user must provide password or re-upload)
  
- **Invalid Format**: Not a valid PDF file
  - Error Code: `PDF_INVALID_FORMAT`
  - Recoverable: No

- **File Not Found**: Storage path invalid or file missing
  - Error Code: `PDF_FILE_NOT_FOUND`
  - Recoverable: Yes (retry after file is available)

#### Bank Detection Errors (Category: DETECTION_ERROR)
- **Unknown Bank**: Cannot identify bank from text
  - Error Code: `DETECTION_UNKNOWN_BANK`
  - Recoverable: No (needs manual intervention or bank connection context)
  
- **Low Confidence**: Bank detected with low confidence
  - Error Code: `DETECTION_LOW_CONFIDENCE`
  - Recoverable: Yes (use bank from connection if available)

#### Parsing Errors (Category: PARSING_ERROR)
- **Parser Not Found**: No parser available for detected bank
  - Error Code: `PARSER_NOT_AVAILABLE`
  - Recoverable: No (bank format not yet supported)
  
- **Format Mismatch**: Detected bank but format doesn't match expected
  - Error Code: `PARSER_FORMAT_MISMATCH`
  - Recoverable: No (format may have changed)
  
- **Incomplete Data**: Parser couldn't extract all required fields
  - Error Code: `PARSER_INCOMPLETE_DATA`
  - Recoverable: Partial (save what was extracted, log warnings)
  
- **Unsupported Format**: Statement format not supported by parser
  - Error Code: `PARSER_UNSUPPORTED_FORMAT`
  - Recoverable: No

#### Storage Errors (Category: STORAGE_ERROR)
- **Database Connection**: Connection timeout or failure
  - Error Code: `STORAGE_DB_CONNECTION`
  - Recoverable: Yes (retry)
  
- **Constraint Violation**: Unique constraint or foreign key violation
  - Error Code: `STORAGE_CONSTRAINT_VIOLATION`
  - Recoverable: Depends (may be idempotency issue)
  
- **Transaction Rollback**: Transaction failed and rolled back
  - Error Code: `STORAGE_TRANSACTION_FAILED`
  - Recoverable: Yes (retry)

#### Validation Errors (Category: VALIDATION_ERROR)
- **Invalid Date Range**: Statement dates are invalid
  - Error Code: `VALIDATION_INVALID_DATES`
  - Recoverable: No
  
- **Missing Required Fields**: Critical data missing
  - Error Code: `VALIDATION_MISSING_FIELDS`
  - Recoverable: No

---

## 6. Error Handling Architecture

### 6.1 Error Classification

#### Recoverable Errors
Errors that can be automatically retried:

- **Temporary Database Issues**: Connection timeouts, deadlocks
- **File Access Issues**: Temporary unavailability
- **Network Issues**: S3 access failures (if using S3)
- **Lock Conflicts**: Concurrent processing attempts

**Retry Strategy**:
- Max 3 attempts
- Exponential backoff (1s, 2s, 4s)
- Reset on success

#### Non-Recoverable Errors
Errors that require manual intervention:

- **Corrupted PDF**: File is unreadable
- **Unknown Bank Format**: No parser available
- **Invalid Statement Format**: Statement doesn't match expected structure
- **Encrypted PDF**: Requires password
- **Validation Failures**: Invalid or missing critical data

**Action**:
- Mark ingestion as FAILED
- Log detailed error to `parser_error` table
- Send notification (future: webhook/email)
- Allow manual retry via API (if format updated)

---

### 6.2 Error Handling by Stage

#### PDF Extraction Errors
```java
try {
    TextExtractionResult result = textExtractor.extract(...);
} catch (PdfCorruptedException e) {
    markFailed(ingestion, ErrorCategory.PDF_ERROR, "PDF_CORRUPTED", e);
    return; // Non-recoverable
} catch (PdfEncryptedException e) {
    markFailed(ingestion, ErrorCategory.PDF_ERROR, "PDF_ENCRYPTED", e);
    return; // Non-recoverable
} catch (IOException e) {
    // Recoverable - file access issue
    scheduleRetry(ingestion, e);
}
```

#### Bank Detection Errors
```java
try {
    BankDetectionResult result = bankDetector.detect(text, connectionId);
    if (result.confidence == LOW && !hasFallbackBank(connectionId)) {
        markFailed(ingestion, ErrorCategory.DETECTION_ERROR, 
                  "DETECTION_LOW_CONFIDENCE", "Cannot confidently detect bank");
        return;
    }
} catch (DetectionException e) {
    // Use bank from connection if available
    if (hasBankConnection(connectionId)) {
        useConnectionBank(connectionId);
    } else {
        markFailed(ingestion, ErrorCategory.DETECTION_ERROR, 
                  "DETECTION_UNKNOWN_BANK", e);
    }
}
```

#### Parsing Errors
```java
try {
    ParserResult result = parser.parse(text, detection);
    if (!result.isComplete()) {
        logWarning(ingestion, "PARSER_INCOMPLETE_DATA", result.warnings);
        // Continue with partial data
    }
} catch (ParserNotFoundException e) {
    markFailed(ingestion, ErrorCategory.PARSING_ERROR, 
              "PARSER_NOT_AVAILABLE", e);
    return; // Non-recoverable
} catch (FormatMismatchException e) {
    markFailed(ingestion, ErrorCategory.PARSING_ERROR, 
              "PARSER_FORMAT_MISMATCH", e);
    return; // Non-recoverable
}
```

#### Storage Errors
```java
try {
    transactionWriter.saveParserResult(result, context);
    markProcessed(ingestion);
} catch (DataIntegrityViolationException e) {
    // Check if duplicate (idempotency)
    if (isDuplicateError(e)) {
        markProcessed(ingestion); // Already processed
    } else {
        markFailed(ingestion, ErrorCategory.STORAGE_ERROR, 
                  "STORAGE_CONSTRAINT_VIOLATION", e);
    }
} catch (TransactionException e) {
    // Recoverable database error
    scheduleRetry(ingestion, e);
}
```

---

### 6.3 Error Logging

**Error Log Structure**:
- **Error Code**: Machine-readable error identifier
- **Error Message**: Human-readable description
- **Stack Trace**: Full exception stack (for debugging)
- **Context**: JSON object with relevant data (file name, bank, etc.)
- **Category**: Error category for filtering
- **Ingestion Reference**: Link back to ingestion record

**Error Log Queries**:
- Find all errors for an ingestion
- Find all errors of a specific category
- Find all errors for a tenant
- Find errors by date range

---

### 6.4 Retry Count

**Retry Tracking**:
- Store retry count in ingestion entity or separate retry log
- Increment on each retry attempt
- Reset on success
- Max retry limit: 3 (configurable)

**Retry Delay**:
- Exponential backoff: 2^attempt seconds
- First retry: 1 second
- Second retry: 2 seconds
- Third retry: 4 seconds

**After Max Retries**:
- Mark ingestion as FAILED (terminal)
- Log final error
- Send notification (future)

---

### 6.5 Dead-Letter Queue (Future Support)

**Purpose**: Store permanently failed ingestions for manual review.

**Future Implementation**:
- Separate table: `ingestion_dead_letter`
- Move FAILED ingestions after max retries
- Manual review and reprocessing interface
- Admin dashboard for dead-letter queue

**Dead-Letter Criteria**:
- Max retries exceeded
- Non-recoverable errors
- Manual flagging for review

---

## 7. Performance & Scalability Notes

### 7.1 Multi-Threaded Processing

**Design Considerations**:
- **Thread Pool**: Fixed thread pool size (configurable, default: 5)
- **Concurrency Control**: Process one ingestion per tenant at a time (optional)
- **Lock Mechanism**: Database-level locks on ingestion records
- **Resource Management**: Limit concurrent PDF processing (memory-intensive)

**Configuration**:
```yaml
asp:
  ingestion:
    processor:
      thread-pool-size: 5
      max-concurrent-per-tenant: 1  # Optional tenant isolation
```

**Benefits**:
- Parallel processing of multiple ingestions
- Better resource utilization
- Faster overall throughput

**Risks**:
- Memory consumption (large PDFs)
- Database connection pool exhaustion
- File I/O contention

---

### 7.2 Batching

**Batch Processing**:
- Process multiple ingestions in batches
- Batch size: 10-20 ingestions (configurable)
- Process batch within single transaction (optional)

**Batch Strategy**:
- Poll for N pending ingestions
- Process all in batch
- Update statuses together
- Reduces database round-trips

**Configuration**:
```yaml
asp:
  ingestion:
    processor:
      batch-size: 10
      batch-transaction: false  # Each ingestion in separate transaction
```

---

### 7.3 Memory Constraints

**PDF Processing Memory**:
- Large PDFs can consume significant memory
- Consider streaming parsers for very large files
- Set memory limits per processing thread
- Monitor JVM heap usage

**Mitigation Strategies**:
- Stream PDF parsing (avoid loading full file in memory)
- Process one page at a time
- Limit concurrent processing based on available memory
- Use disk-based temporary storage for large files

**Configuration**:
```yaml
asp:
  ingestion:
    processor:
      max-pdf-size-mb: 50
      max-memory-per-thread-mb: 512
```

---

### 7.4 Very Large PDFs

**Handling Strategy**:
- Detect file size before processing
- For files > 50MB, use streaming parser
- Process pages incrementally
- Store intermediate results

**Optimization**:
- Lazy loading of PDF pages
- Incremental text extraction
- Batch database writes (not one-by-one)

---

### 7.5 Future SQS/S3 Worker Pipeline

**Architecture Vision**:
```
S3 Event → SQS Queue → Lambda/Worker → Processing → Results
```

**Benefits**:
- Auto-scaling workers
- Decoupled architecture
- Better fault tolerance
- Event-driven processing

**Migration Path**:
- Keep local processing option
- Add S3 event listener
- Queue ingestions to SQS
- Workers pull from SQS
- Process and update status

**Components**:
- **S3 Event Listener**: Detects new PDF uploads
- **SQS Queue**: Queues ingestion jobs
- **Worker Lambda/Service**: Processes jobs from queue
- **Result Handler**: Updates ingestion status

---

### 7.6 Observability & Logging

**Structured Logging**:
- Log all pipeline stages
- Include ingestion ID, tenant ID in all logs
- Log performance metrics (processing time per stage)
- Log error details with context

**Metrics to Track**:
- Processing time per stage (extraction, detection, parsing, saving)
- Success/failure rates
- Error rates by category
- Throughput (ingestions per hour)
- Queue depth (if using queues)

**Logging Format**:
```
[INGESTION:{ingestionId}] [TENANT:{tenantId}] [STAGE:{stage}] Processing started
[INGESTION:{ingestionId}] [STAGE:{stage}] Completed in {duration}ms
[INGESTION:{ingestionId}] [ERROR:{errorCode}] Error occurred: {message}
```

**Monitoring**:
- Dashboard for ingestion status distribution
- Alert on high failure rates
- Alert on processing delays
- Track parser performance per bank

---

## 8. Future Extensions

### 8.1 S3 Storage Instead of Local

**Migration Path**:
1. Implement `S3DocumentStorageService`
2. Make storage service selectable via configuration
3. Support both local and S3 simultaneously (hybrid)
4. Migrate existing files to S3 (optional)

**Benefits**:
- Scalable storage
- No local disk space constraints
- Better for distributed deployments
- Lifecycle policies for cleanup

**Configuration**:
```yaml
asp:
  storage:
    provider: s3  # or "local"
    s3:
      bucket: asp-platform-ingestions
      region: us-east-1
      prefix: tenants/
```

---

### 8.2 Async Ingestion Workers

**Architecture**:
- Message queue (SQS, RabbitMQ, Kafka)
- Worker pool processes jobs asynchronously
- Webhook/notification on completion

**Benefits**:
- Non-blocking API responses
- Better scalability
- Fault tolerance
- Retry mechanism via queue

**Implementation**:
- Upload endpoint returns immediately (status: PENDING)
- Queue job to worker pool
- Worker processes asynchronously
- Webhook/API callback on completion

---

### 8.3 Multi-Bank Parsing

**Current**: One parser per bank

**Future Enhancement**:
- Generic parser for similar formats
- ML-based format detection
- Template-based parsing
- User-defined parsers (advanced)

---

### 8.4 ML-Based Table Extraction (Optional)

**Purpose**: Improve parsing accuracy for complex statements.

**Techniques**:
- Computer vision for table detection
- OCR with table structure recognition
- ML models for field extraction
- Training on historical data

**Use Cases**:
- Scanned PDFs (OCR)
- Complex table layouts
- Multi-column statements
- Handwritten annotations (future)

---

### 8.5 OCR Fallback

**Current**: Text-based PDF parsing only

**Future Enhancement**:
- Detect scanned PDFs (image-based)
- Use OCR (Tesseract, AWS Textract) for text extraction
- Fallback when text extraction returns empty/low confidence

**Implementation**:
```java
TextExtractionResult result = textExtractor.extract(...);
if (result.isScanned || result.text.isEmpty()) {
    result = ocrExtractor.extract(pdfFile);  // OCR fallback
}
```

**OCR Options**:
- **Tesseract**: Open-source, self-hosted
- **AWS Textract**: Cloud service, better accuracy
- **Google Cloud Vision**: Alternative cloud option

---

### 8.6 Additional Enhancements

**Reconciliation**:
- Match transactions across statements
- Detect duplicates
- Balance reconciliation
- Gap detection (missing statements)

**Data Enrichment**:
- Categorize transactions (ML-based)
- Add merchant information
- Geocode transaction locations
- Link related transactions

**Analytics**:
- Spending patterns
- Account balance trends
- Transaction frequency
- Category breakdowns

**Webhooks**:
- Notify tenants on processing completion
- Error notifications
- Status change events

---

## Appendix: Implementation Phases

### Phase 1: Core Pipeline (MVP)
- Text extraction
- Basic bank detection
- Single bank parser (e.g., CIH)
- Database storage
- Error handling

### Phase 2: Multi-Bank Support
- Additional bank parsers (Attijari, BMCE)
- Parser registry
- Improved bank detection

### Phase 3: Robustness
- Retry mechanism
- Better error handling
- Idempotency
- Performance optimization

### Phase 4: Scalability
- Async workers
- Queue integration
- S3 storage
- Monitoring & observability

### Phase 5: Advanced Features
- OCR support
- ML-based extraction
- Reconciliation
- Analytics

---

**Document Version**: 1.0  
**Last Updated**: 2025-11-22  
**Status**: Architecture Design (Implementation: Pending)

