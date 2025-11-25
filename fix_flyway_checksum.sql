-- SQL script to fix Flyway checksum mismatch for migration V9
-- Run this in your PostgreSQL database, then repair Flyway

-- First, ensure the constraint is dropped (in case V9 didn't work)
ALTER TABLE normalized_transaction 
    DROP CONSTRAINT IF EXISTS normalized_transaction_account_id_ingestion_id_date_amount__key;

ALTER TABLE normalized_transaction 
    DROP CONSTRAINT IF EXISTS normalized_transaction_account_id_ingestion_id_date_amount_raw_description_key;

-- Update Flyway schema history to match the new checksum
-- The new checksum for V9 is: -5829880
UPDATE flyway_schema_history 
SET checksum = -5829880 
WHERE version = '9' AND description LIKE '%remove_transaction_deduplication%';

-- Verify the update
SELECT version, description, checksum, installed_on 
FROM flyway_schema_history 
WHERE version = '9';

