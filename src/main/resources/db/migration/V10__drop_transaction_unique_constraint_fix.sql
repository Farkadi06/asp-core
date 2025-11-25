-- Fix: Drop the unique constraint that prevents legitimate duplicate transactions
-- This is a follow-up to V9 in case the constraint name was incorrect
-- CIH statements can contain genuine duplicates (e.g., multiple identical mobile recharges)

-- Drop the constraint using the actual constraint name from the error
ALTER TABLE normalized_transaction 
    DROP CONSTRAINT IF EXISTS normalized_transaction_account_id_ingestion_id_date_amount__key;

-- Also try alternative possible names
ALTER TABLE normalized_transaction 
    DROP CONSTRAINT IF EXISTS normalized_transaction_account_id_ingestion_id_date_amount_raw_description_key;

-- Note: We still rely on ingestion-level idempotency (same ingestion won't be processed twice)
-- But within a single ingestion, duplicate transaction rows are allowed

