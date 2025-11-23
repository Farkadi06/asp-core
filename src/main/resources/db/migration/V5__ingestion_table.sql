-- Create ingestion table
CREATE TABLE ingestion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    bank_connection_id UUID NOT NULL REFERENCES bank_connection(id) ON DELETE CASCADE,
    user_ref VARCHAR NOT NULL,
    status VARCHAR NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    source VARCHAR NOT NULL CHECK (source IN ('MANUAL_UPLOAD')),
    ingestion_type VARCHAR NOT NULL CHECK (ingestion_type IN ('PDF_STATEMENT')),
    original_file_name VARCHAR,
    file_size BIGINT,
    storage_path VARCHAR,
    error_code VARCHAR,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITH TIME ZONE
);

-- Create indexes
CREATE INDEX idx_ingestion_tenant_id_user_ref ON ingestion(tenant_id, user_ref);
CREATE INDEX idx_ingestion_bank_connection_id ON ingestion(bank_connection_id);
CREATE INDEX idx_ingestion_status ON ingestion(status);
CREATE INDEX idx_ingestion_tenant_id ON ingestion(tenant_id);

-- Enable Row Level Security on ingestion table
ALTER TABLE ingestion ENABLE ROW LEVEL SECURITY;

-- Create policy for tenant isolation on ingestion
-- This policy ensures tenants can only see their own ingestion records
CREATE POLICY ingestion_tenant_isolation ON ingestion
    USING (tenant_id = current_setting('asp.tenant_id', true)::uuid);

