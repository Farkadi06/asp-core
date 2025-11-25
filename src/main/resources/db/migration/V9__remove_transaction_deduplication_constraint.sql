-- Remove unique constraint that prevents legitimate duplicate transactions
-- CIH statements can contain genuine duplicates (e.g., multiple identical mobile recharges)
-- We must allow all transaction rows from the statement to be saved

-- Drop the constraint using the actual constraint name (PostgreSQL auto-generated name)
ALTER TABLE normalized_transaction 
    DROP CONSTRAINT IF EXISTS normalized_transaction_account_id_ingestion_id_date_amount__key;

-- Also try the full name in case it exists
ALTER TABLE normalized_transaction 
    DROP CONSTRAINT IF EXISTS normalized_transaction_account_id_ingestion_id_date_amount_raw_description_key;

-- Note: We still rely on ingestion-level idempotency (same ingestion won't be processed twice)
-- But within a single ingestion, duplicate transaction rows are allowed

