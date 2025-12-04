-- Create user table for dashboard authentication
-- Users belong to tenants (reusing existing tenant table)
CREATE TABLE IF NOT EXISTS "user" (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    provider VARCHAR(50) NOT NULL,
    provider_user_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMP WITH TIME ZONE
);

-- Create index on tenant_id for faster lookups
CREATE INDEX IF NOT EXISTS idx_user_tenant_id ON "user"(tenant_id);

-- Create index on email (already unique, but index helps with lookups)
CREATE INDEX IF NOT EXISTS idx_user_email ON "user"(email);

-- Create user_session table for dashboard session management
CREATE TABLE IF NOT EXISTS user_session (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES "user"(id) ON DELETE CASCADE,
    session_token VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    invalidated_at TIMESTAMP WITH TIME ZONE
);

-- Create index on session_token for fast lookups
CREATE INDEX IF NOT EXISTS idx_user_session_token ON user_session(session_token);

-- Create index on user_id for session cleanup queries
CREATE INDEX IF NOT EXISTS idx_user_session_user_id ON user_session(user_id);

-- Create index on expires_at for cleanup of expired sessions
CREATE INDEX IF NOT EXISTS idx_user_session_expires_at ON user_session(expires_at);

