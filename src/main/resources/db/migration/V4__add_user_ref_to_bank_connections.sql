-- Add user_ref column to bank_connection table
ALTER TABLE bank_connection
ADD COLUMN user_ref VARCHAR NOT NULL DEFAULT 'default';

-- Remove the default after adding the column
ALTER TABLE bank_connection
ALTER COLUMN user_ref DROP DEFAULT;

-- Create index for user_ref
CREATE INDEX idx_bank_connection_user_ref ON bank_connection(user_ref);

-- Note: RLS policy on bank_connection already uses tenant_id for isolation
-- No changes needed to RLS policy as userRef filtering happens at application level

