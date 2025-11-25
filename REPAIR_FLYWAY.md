# Flyway Repair Instructions

## Problem
Migration V9 was already applied to the database, but then the file was modified. Flyway detects a checksum mismatch and fails to start.

## Solution Options

### Option 1: Repair Flyway (Recommended)
Run this command to repair the checksum:

```bash
./gradlew flywayRepair
```

Or if using Flyway CLI directly:
```bash
flyway repair -url=jdbc:postgresql://localhost:5432/asp_platform -user=asp -password=asp
```

### Option 2: Temporarily Disable Validation
If repair doesn't work, you can temporarily disable validation in `application.yml`:

```yaml
spring:
  flyway:
    validate-on-migrate: false
```

Then restart the app, and re-enable validation.

### Option 3: Manual Database Fix
If the constraint still exists, connect to PostgreSQL and run:

```sql
ALTER TABLE normalized_transaction 
    DROP CONSTRAINT IF EXISTS normalized_transaction_account_id_ingestion_id_date_amount__key;
```

Then repair Flyway:
```bash
./gradlew flywayRepair
```

## After Repair
Once Flyway is repaired, V10 migration will run automatically on next startup to ensure the constraint is dropped.

