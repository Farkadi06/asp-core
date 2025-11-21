-- Create bank table
CREATE TABLE bank (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(150) NOT NULL UNIQUE,
    country VARCHAR(50) NOT NULL,
    connection_type VARCHAR(30) NOT NULL CHECK (connection_type IN ('OAUTH', 'PDF', 'SCRAPER', 'UNKNOWN')),
    icon_url VARCHAR(300),
    status VARCHAR(30) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for bank table
CREATE INDEX idx_bank_slug ON bank(slug);
CREATE INDEX idx_bank_status ON bank(status);
CREATE INDEX idx_bank_connection_type ON bank(connection_type);

-- Create bank_connection table
CREATE TABLE bank_connection (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    bank_id UUID NOT NULL REFERENCES bank(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING', 'WAITING_UPLOAD', 'WAITING_OAUTH', 'CONNECTED', 'FAILED')),
    step VARCHAR(50),
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for bank_connection table
CREATE INDEX idx_bank_connection_tenant_id ON bank_connection(tenant_id);
CREATE INDEX idx_bank_connection_bank_id ON bank_connection(bank_id);
CREATE INDEX idx_bank_connection_status ON bank_connection(status);

-- Enable Row Level Security on bank_connection table
ALTER TABLE bank_connection ENABLE ROW LEVEL SECURITY;

-- Create policy for tenant isolation on bank_connection
-- This policy ensures tenants can only see their own bank connections
CREATE POLICY bank_connection_tenant_isolation ON bank_connection
    USING (tenant_id = current_setting('asp.tenant_id', true)::uuid);

