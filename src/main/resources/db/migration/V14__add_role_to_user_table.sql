-- Add role column to user table
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS role VARCHAR(50) NOT NULL DEFAULT 'OWNER';

-- Create index on role for faster lookups
CREATE INDEX IF NOT EXISTS idx_user_role ON "user"(role);

