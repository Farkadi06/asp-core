-- Update ingestion table status constraint to include new statuses
ALTER TABLE ingestion DROP CONSTRAINT IF EXISTS ingestion_status_check;

ALTER TABLE ingestion ADD CONSTRAINT ingestion_status_check 
    CHECK (status IN ('PENDING', 'PROCESSING', 'PARSED', 'SAVING', 'PROCESSED', 'FAILED'));

