# Normalized Data Persistence

## Overview

The normalized data persistence layer is responsible for writing parsed bank statement data to the database. It handles accounts, transactions, and account snapshots with full idempotency, tenant isolation, and error handling.

**Key Features:**
- Account upsert (create or update existing accounts)
- Transaction insertion with duplicate detection
- Account snapshot creation (one per ingestion)
- Full idempotency support (safe to re-run)
- Tenant and userRef isolation
- Comprehensive error logging

## Architecture

### Component: DefaultTransactionWriter

**Location**: `src/main/java/com/asp/parsing/writer/DefaultTransactionWriter.java`

**Interface**: `src/main/java/com/asp/parsing/writer/TransactionWriter.java`

The writer implements the `TransactionWriter` interface and is called by `DefaultIngestionProcessorService` after successful parsing.

### Processing Flow

```
ParserResult
    ↓
DefaultTransactionWriter.saveParserResult()
    ↓
1. Upsert Accounts (create or update)
    ↓
2. Save Transactions (with idempotency check)
    ↓
3. Save Snapshots (one per account per ingestion)
    ↓
Success → Ingestion status = PROCESSED
Error → Ingestion status = FAILED + ParserError logged
```

## Database Schema

**Migration**: `V8__normalized_accounts_transactions_snapshots.sql`

### Normalized Account Table

```sql
CREATE TABLE normalized_account (
    account_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ingestion_id UUID NOT NULL REFERENCES ingestion(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    user_ref VARCHAR NOT NULL,
    bank_connection_id UUID NOT NULL REFERENCES bank_connection(id) ON DELETE CASCADE,
    account_number VARCHAR NOT NULL,
    iban VARCHAR,
    account_type VARCHAR NOT NULL,  -- CHECKING, SAVINGS, etc.
    currency VARCHAR(3) NOT NULL DEFAULT 'MAD',
    balance DECIMAL(15, 2),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Unique constraint: one account per tenant/user/bank connection
    UNIQUE(tenant_id, user_ref, account_number, bank_connection_id)
);
```

**Key Fields:**
- `account_id` - Primary key (UUID)
- `account_number` - Bank account number (e.g., "1234567890")
- `iban` - International Bank Account Number (optional)
- `account_type` - CHECKING, SAVINGS, etc.
- `balance` - Current account balance

**Unique Constraint**: Prevents duplicate accounts for the same tenant/user/bank connection combination.

### Normalized Transaction Table

```sql
CREATE TABLE normalized_transaction (
    transaction_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ingestion_id UUID NOT NULL REFERENCES ingestion(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES normalized_account(account_id) ON DELETE CASCADE,
    user_ref VARCHAR NOT NULL,
    date DATE NOT NULL,
    description TEXT,
    amount DECIMAL(15, 2) NOT NULL,  -- Signed: positive = credit, negative = debit
    currency VARCHAR(3) NOT NULL DEFAULT 'MAD',
    category VARCHAR,
    merchant VARCHAR,
    raw_description TEXT,  -- Original description from bank statement
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Idempotency: prevent duplicate transactions
    UNIQUE(account_id, ingestion_id, date, amount, raw_description)
);
```

**Key Fields:**
- `transaction_id` - Primary key (UUID)
- `amount` - Signed decimal (positive = credit, negative = debit)
- `date` - Transaction date
- `raw_description` - Original bank description (for idempotency)
- `description` - Cleaned/normalized description

**Idempotency Constraint**: Prevents duplicate transactions based on account, ingestion, date, amount, and raw description.

### Normalized Account Snapshot Table

```sql
CREATE TABLE normalized_account_snapshot (
    snapshot_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ingestion_id UUID NOT NULL REFERENCES ingestion(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES normalized_account(account_id) ON DELETE CASCADE,
    user_ref VARCHAR NOT NULL,
    balance DECIMAL(15, 2) NOT NULL,
    date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- One snapshot per account per ingestion
    UNIQUE(account_id, ingestion_id)
);
```

**Key Fields:**
- `snapshot_id` - Primary key (UUID)
- `balance` - Account balance at snapshot time
- `date` - Snapshot date (usually statement end date)

**Unique Constraint**: Ensures only one snapshot per account per ingestion.

### Row Level Security (RLS)

All three tables have RLS enabled for tenant isolation:

```sql
ALTER TABLE normalized_account ENABLE ROW LEVEL SECURITY;
ALTER TABLE normalized_transaction ENABLE ROW LEVEL SECURITY;
ALTER TABLE normalized_account_snapshot ENABLE ROW LEVEL SECURITY;

-- Policies ensure tenants can only see their own data
CREATE POLICY normalized_account_tenant_isolation ON normalized_account
    USING (tenant_id = current_setting('asp.tenant_id', true)::uuid);
```

## Implementation Details

### 1. Account Upsert Logic

**Method**: `upsertAccounts()`

**Process:**
1. For each account in `ParserResult`:
   - Lookup existing account by: `(tenantId, userRef, accountNumber, bankConnectionId)`
   - If exists:
     - Update mutable fields: `iban`, `accountType`, `currency`, `balance`
     - Save updated account
   - If not exists:
     - Create new `NormalizedAccount` entity
     - Set all required fields from context and parser result
     - Save new account
2. Build account map: `accountNumber → NormalizedAccount` for transaction linking

**Code Example:**
```java
Optional<NormalizedAccount> existing = accountRepository
    .findByTenantIdAndUserRefAndAccountNumberAndBankConnectionId(
        context.getTenantId(),
        context.getUserRef(),
        normalizedAccount.getAccountNumber(),
        context.getBankConnectionId()
    );

if (existing.isPresent()) {
    NormalizedAccount account = existing.get();
    // Update mutable fields
    account.setIban(normalizedAccount.getIban());
    account.setAccountType(normalizedAccount.getAccountType());
    account.setCurrency(normalizedAccount.getCurrency());
    account.setBalance(normalizedAccount.getBalance());
    accountRepository.save(account);
} else {
    // Create new account
    NormalizedAccount newAccount = NormalizedAccount.builder()
        .tenant(ingestion.getTenant())
        .bankConnection(ingestion.getBankConnection())
        .ingestion(ingestion)
        .userRef(context.getUserRef())
        .accountNumber(normalizedAccount.getAccountNumber())
        // ... set other fields
        .build();
    accountRepository.save(newAccount);
}
```

### 2. Transaction Insertion with Idempotency

**Method**: `saveTransactionsInternal()`

**Process:**
1. For each transaction:
   - Map `accountNumber` to `NormalizedAccount` entity (from account map)
   - If account not found → log `ParserError` and skip transaction
   - Validate required fields (date, amount)
   - Check for duplicate using repository method:
     ```java
     findByAccount_AccountIdAndIngestionIdAndDateAndAmountAndRawDescription(
         accountId, ingestionId, date, amount, rawDescription
     )
     ```
   - If duplicate exists → skip (idempotent)
   - If not duplicate:
     - Create `NormalizedTransaction` entity
     - Set all fields from parser result
     - Save transaction
   - Handle `DataIntegrityViolationException` (DB-level unique constraint) as duplicate

**Idempotency Strategy:**
- **Application-level**: Check before insert using repository query
- **Database-level**: Unique constraint as fallback (`UNIQUE(account_id, ingestion_id, date, amount, raw_description)`)
- **Behavior**: Duplicate transactions are silently skipped (no error)

### 3. Snapshot Creation

**Method**: `saveSnapshotsInternal()`

**Process:**
1. For each snapshot in `ParserResult`:
   - Map `accountNumber` to `NormalizedAccount` entity
   - If account not found → log error and skip
   - Check if snapshot already exists: `findByAccount_AccountIdAndIngestionId(accountId, ingestionId)`
   - If exists → skip (idempotent)
   - If not exists:
     - Determine snapshot date (from parser or statement period)
     - Determine balance (closing balance → opening balance → account balance → 0)
     - Create `NormalizedAccountSnapshot` entity
     - Save snapshot

**Snapshot Date Priority:**
1. `snapshot.getSnapshotDate()` (from parser)
2. `statementPeriod.getEndDate()` (from parser result)
3. `LocalDate.now()` (fallback)

**Balance Priority:**
1. `snapshot.getClosingBalance()`
2. `snapshot.getOpeningBalance()`
3. Account balance from entity
4. `BigDecimal.ZERO` (fallback)

## Idempotency

### What is Idempotency?

Idempotency ensures that running the same operation multiple times produces the same result without side effects. In this context, it means:

- **Re-processing the same ingestion** won't create duplicate accounts, transactions, or snapshots
- **Re-running failed ingestions** is safe (won't create duplicates)
- **Partial failures** can be safely retried

### Idempotency Mechanisms

#### 1. Account Idempotency

**Constraint**: `UNIQUE(tenant_id, user_ref, account_number, bank_connection_id)`

**Behavior**: 
- First ingestion creates account
- Subsequent ingestions update existing account (upsert)
- Same account across multiple ingestions = same database record

#### 2. Transaction Idempotency

**Constraint**: `UNIQUE(account_id, ingestion_id, date, amount, raw_description)`

**Behavior**:
- Application checks for duplicate before insert
- Database constraint as safety net
- Duplicate transactions are silently skipped

**Why 5 fields?**
- `account_id` - Which account
- `ingestion_id` - Which statement
- `date` - Transaction date
- `amount` - Transaction amount
- `raw_description` - Original bank description

These 5 fields uniquely identify a transaction within a statement.

#### 3. Snapshot Idempotency

**Constraint**: `UNIQUE(account_id, ingestion_id)`

**Behavior**:
- One snapshot per account per ingestion
- Re-processing same ingestion won't create duplicate snapshot
- Checked before insert

### Testing Idempotency

```java
// First run
writer.saveParserResult(parserResult, context);
// → Creates accounts, transactions, snapshots

// Second run (same data)
writer.saveParserResult(parserResult, context);
// → Updates accounts, skips duplicate transactions/snapshots
// → No errors, no duplicates
```

## Error Handling

### Error Categories

Errors are logged as `ParserError` with category `STORAGE_ERROR`:

1. **Missing Account**: Transaction/snapshot references unknown account
   - **Error Code**: `SAVING_MISSING_ACCOUNT`
   - **Action**: Log error, skip transaction/snapshot, continue processing

2. **Missing Required Fields**: Transaction missing date or amount
   - **Error Code**: `SAVING_MISSING_FIELD`
   - **Action**: Log error, skip transaction, continue processing

3. **Database Constraint Violation**: Unexpected duplicate (shouldn't happen with checks)
   - **Error Code**: `SAVING_DUPLICATE`
   - **Action**: Treat as duplicate, continue processing

4. **General Persistence Errors**: Any other database error
   - **Error Code**: `SAVING_ERROR`
   - **Action**: Log error, mark ingestion as FAILED, throw exception

### Error Logging

**Component**: `ParserErrorLogger`

**Location**: `src/main/java/com/asp/parsing/error/ParserErrorLogger.java`

Errors are persisted to `parser_error` table with:
- Error category (`STORAGE_ERROR`)
- Error code (specific error type)
- Error message (human-readable)
- Stack trace (if exception)
- Context (JSON metadata)

### Ingestion Status Updates

**Success Path:**
```
PENDING → PROCESSING → PARSED → SAVING → PROCESSED
```

**Failure Path:**
```
PENDING → PROCESSING → ... → FAILED
```

**Status Transitions:**
- `DefaultIngestionProcessorService` sets status to `SAVING` before calling writer
- Writer throws exception on critical errors
- Processor catches exception and sets status to `FAILED`
- Processor logs `ParserError` via `ParserErrorLogger`

## Testing Guide

### Prerequisites

1. **Database Setup**: PostgreSQL running with migrations applied
2. **Test Tenant**: Create a tenant and API key
3. **Bank Connection**: Create a bank connection for testing
4. **Test PDF**: A valid bank statement PDF

### Manual Testing via API

#### Step 1: Upload PDF Ingestion

```bash
curl -X POST http://localhost:8080/v1/ingestions \
  -H "X-Api-Key: asp_live_<prefix>_<secret>" \
  -F "file=@statement.pdf" \
  -F "bankConnectionId=<bank-connection-uuid>"
```

**Response:**
```json
{
  "id": "7a7b5761-2c01-4b06-9b10-4fbdc8741ff2",
  "bankConnectionId": "...",
  "userRef": "user_sara_001",
  "status": "PENDING",
  "source": "MANUAL_UPLOAD",
  "ingestionType": "PDF_STATEMENT",
  "originalFileName": "statement.pdf",
  "fileSize": 12345,
  "storagePath": "tenants/.../ingestions/.../original/statement.pdf",
  "createdAt": "2025-11-24T10:00:00Z"
}
```

#### Step 2: Trigger Processing (if scheduler enabled)

The ingestion will be processed automatically if a scheduler is configured. Otherwise, you can manually trigger processing via:

```java
// In a test or admin endpoint
ingestionProcessorService.processIngestion(ingestionId);
```

#### Step 3: Check Ingestion Status

```bash
curl -X GET http://localhost:8080/v1/ingestions/<ingestion-id> \
  -H "X-Api-Key: asp_live_<prefix>_<secret>"
```

**Success Response:**
```json
{
  "id": "...",
  "status": "PROCESSED",
  "processedAt": "2025-11-24T10:05:00Z",
  ...
}
```

**Failure Response:**
```json
{
  "id": "...",
  "status": "FAILED",
  "errorCode": "STORAGE_ERROR",
  "errorMessage": "Failed to save data: ...",
  ...
}
```

#### Step 4: Query Normalized Data

**Check Accounts:**
```sql
SELECT account_id, account_number, account_type, currency, balance
FROM normalized_account
WHERE tenant_id = '<tenant-uuid>'
  AND user_ref = 'user_sara_001';
```

**Check Transactions:**
```sql
SELECT transaction_id, date, amount, description, raw_description
FROM normalized_transaction
WHERE tenant_id = '<tenant-uuid>'
  AND user_ref = 'user_sara_001'
ORDER BY date DESC
LIMIT 10;
```

**Check Snapshots:**
```sql
SELECT snapshot_id, account_id, balance, date
FROM normalized_account_snapshot
WHERE tenant_id = '<tenant-uuid>'
  AND ingestion_id = '<ingestion-uuid>';
```

### Unit Testing

**Test Class**: `src/test/java/com/asp/parsing/writer/DefaultTransactionWriterTest.java` (to be created)

**Key Test Cases:**

1. **Account Upsert**
   ```java
   @Test
   void testAccountUpsert_CreatesNewAccount() {
       // Given: No existing account
       // When: Save parser result with account
       // Then: Account created in database
   }
   
   @Test
   void testAccountUpsert_UpdatesExistingAccount() {
       // Given: Account exists
       // When: Save parser result with same account (different balance)
       // Then: Account updated, not duplicated
   }
   ```

2. **Transaction Idempotency**
   ```java
   @Test
   void testTransactionIdempotency_SkipsDuplicates() {
       // Given: Transaction already saved
       // When: Save same transaction again
       // Then: No duplicate created, no error
   }
   ```

3. **Snapshot Idempotency**
   ```java
   @Test
   void testSnapshotIdempotency_OnePerIngestion() {
       // Given: Snapshot already exists for ingestion
       // When: Save same snapshot again
       // Then: No duplicate created
   }
   ```

4. **Error Handling**
   ```java
   @Test
   void testMissingAccount_LogsErrorAndContinues() {
       // Given: Transaction references unknown account
       // When: Save transactions
       // Then: ParserError logged, transaction skipped, processing continues
   }
   ```

### Integration Testing

**Test Flow:**
1. Upload PDF → Ingestion created (PENDING)
2. Trigger processing → Status changes: PENDING → PROCESSING → PARSED → SAVING → PROCESSED
3. Verify data in database:
   - Accounts created/updated
   - Transactions inserted
   - Snapshots created
4. Re-process same ingestion → Verify no duplicates

### Database Verification Queries

**Count Records:**
```sql
-- Accounts per tenant
SELECT COUNT(*) FROM normalized_account WHERE tenant_id = '<uuid>';

-- Transactions per ingestion
SELECT COUNT(*) FROM normalized_transaction WHERE ingestion_id = '<uuid>';

-- Snapshots per ingestion
SELECT COUNT(*) FROM normalized_account_snapshot WHERE ingestion_id = '<uuid>';
```

**Verify Idempotency:**
```sql
-- Check for duplicate transactions (should return 0)
SELECT account_id, ingestion_id, date, amount, raw_description, COUNT(*)
FROM normalized_transaction
GROUP BY account_id, ingestion_id, date, amount, raw_description
HAVING COUNT(*) > 1;

-- Check for duplicate snapshots (should return 0)
SELECT account_id, ingestion_id, COUNT(*)
FROM normalized_account_snapshot
GROUP BY account_id, ingestion_id
HAVING COUNT(*) > 1;
```

**Check Error Logs:**
```sql
SELECT error_category, error_code, error_message, occurred_at
FROM parser_error
WHERE ingestion_id = '<uuid>'
ORDER BY occurred_at DESC;
```

## Example Flows

### Flow 1: First-Time Account Processing

1. **Upload**: User uploads first statement for account "1234567890"
2. **Parse**: Parser extracts account info, transactions, snapshot
3. **Save**:
   - Account created: `account_id = uuid-1`
   - 50 transactions inserted
   - 1 snapshot created
4. **Result**: Ingestion status = PROCESSED

### Flow 2: Subsequent Statement Processing

1. **Upload**: User uploads second statement for same account "1234567890"
2. **Parse**: Parser extracts new transactions
3. **Save**:
   - Account updated (balance, etc.) - same `account_id = uuid-1`
   - 30 new transactions inserted (different dates/amounts)
   - 1 new snapshot created (different `ingestion_id`)
4. **Result**: Ingestion status = PROCESSED, no duplicates

### Flow 3: Re-Processing Failed Ingestion

1. **Initial**: Ingestion fails during parsing (status = FAILED)
2. **Retry**: Admin retries ingestion
3. **Save**:
   - Account upserted (no change if already exists)
   - Transactions checked for duplicates (all skipped if already saved)
   - Snapshot checked for duplicate (skipped if exists)
4. **Result**: Idempotent - no duplicates created

### Flow 4: Error Handling

1. **Upload**: Statement with transaction referencing unknown account
2. **Save**:
   - Account processing succeeds
   - Transaction processing encounters missing account
   - Error logged: `SAVING_MISSING_ACCOUNT`
   - Transaction skipped, processing continues
   - Other transactions saved successfully
3. **Result**: Ingestion status = PROCESSED (partial success), error logged

## Repository Methods

### NormalizedAccountRepository

```java
List<NormalizedAccount> findAllByTenantId(UUID tenantId);
List<NormalizedAccount> findAllByTenantIdAndUserRef(UUID tenantId, String userRef);
List<NormalizedAccount> findAllByTenantIdAndBankConnectionId(UUID tenantId, UUID bankConnectionId);
List<NormalizedAccount> findAllByIngestionId(UUID ingestionId);
Optional<NormalizedAccount> findByTenantIdAndUserRefAndAccountNumberAndBankConnectionId(
    UUID tenantId, String userRef, String accountNumber, UUID bankConnectionId);
```

### NormalizedTransactionRepository

```java
List<NormalizedTransaction> findAllByTenantId(UUID tenantId);
List<NormalizedTransaction> findAllByTenantIdAndUserRef(UUID tenantId, String userRef);
List<NormalizedTransaction> findAllByIngestionId(UUID ingestionId);
List<NormalizedTransaction> findAllByAccount_AccountId(UUID accountId);
List<NormalizedTransaction> findAllByTenantIdAndUserRefAndAccount_AccountId(
    UUID tenantId, String userRef, UUID accountId);
List<NormalizedTransaction> findAllByAccount_AccountIdAndDateBetween(
    UUID accountId, LocalDate startDate, LocalDate endDate);
Optional<NormalizedTransaction> findByAccountIdAndIngestionIdAndDateAndAmountAndRawDescription(
    UUID accountId, UUID ingestionId, LocalDate date, BigDecimal amount, String rawDescription);
```

**Note**: Methods use `Account_AccountId` (with underscore) because Spring Data JPA needs to traverse the relationship `account.accountId` (not `account.id`).

### NormalizedAccountSnapshotRepository

```java
List<NormalizedAccountSnapshot> findAllByTenantId(UUID tenantId);
List<NormalizedAccountSnapshot> findAllByTenantIdAndUserRef(UUID tenantId, String userRef);
List<NormalizedAccountSnapshot> findAllByIngestionId(UUID ingestionId);
List<NormalizedAccountSnapshot> findAllByAccount_AccountId(UUID accountId);
Optional<NormalizedAccountSnapshot> findByAccount_AccountIdAndIngestionId(UUID accountId, UUID ingestionId);
```

## Performance Considerations

### Batch Processing

- Accounts: Processed sequentially (small number, typically 1-3 per statement)
- Transactions: Processed sequentially with duplicate check per transaction
- Snapshots: Processed sequentially (small number)

### Optimization Opportunities

1. **Batch Inserts**: For large transaction lists, consider batch inserts
2. **Bulk Duplicate Check**: Check all transactions at once instead of one-by-one
3. **Caching**: Cache account lookups within same ingestion

### Current Limitations

- No batch operations (each transaction checked individually)
- No transaction batching (each saved separately)
- Account map built in memory (fine for typical statement sizes)

## Troubleshooting

### Issue: "No property 'id' found for type 'NormalizedAccount'"

**Cause**: Repository method uses incorrect property path.

**Solution**: Use `Account_AccountId` instead of `AccountId` in method names.

**Example:**
```java
// ❌ Wrong
findAllByAccountId(UUID accountId)

// ✅ Correct
findAllByAccount_AccountId(UUID accountId)
```

### Issue: Duplicate Key Violation

**Cause**: Unique constraint violation (shouldn't happen with proper checks).

**Solution**: Exception is caught and treated as duplicate. Verify idempotency checks are working.

### Issue: Missing Account Error

**Cause**: Transaction references account that wasn't created.

**Solution**: 
- Check parser logic (accounts should be created before transactions)
- Verify account map is built correctly
- Check for account creation errors

### Issue: Ingestion Stuck in SAVING Status

**Cause**: Exception during save not properly caught.

**Solution**: 
- Check application logs for exceptions
- Check `parser_error` table for error details
- Verify transaction boundaries (@Transactional)

## Related Documentation

- [PDF Ingestion Pipeline](./05-pdf-ingestion-pipeline.md) - Upload and storage
- [Architecture: Ingestion Parsing Pipeline](./architecture/06_ingestion_parsing_pipeline.md) - Full pipeline overview
- [Multi-Tenancy & Authentication](./02-multi-tenancy-and-authentication.md) - RLS and tenant isolation

## Summary

The normalized data persistence layer provides:

✅ **Full idempotency** - Safe to re-run  
✅ **Tenant isolation** - RLS enforced  
✅ **Error resilience** - Partial failures don't stop processing  
✅ **Comprehensive logging** - All errors tracked  
✅ **Transaction safety** - All operations in @Transactional  

The implementation ensures data integrity while allowing safe retries and partial success scenarios.

