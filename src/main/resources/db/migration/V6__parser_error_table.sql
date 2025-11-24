-- Create parser_error table for storing parsing errors
CREATE TABLE parser_error (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ingestion_id UUID NOT NULL REFERENCES ingestion(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    error_category VARCHAR(50) NOT NULL CHECK (error_category IN ('PDF_ERROR', 'DETECTION_ERROR', 'PARSING_ERROR', 'STORAGE_ERROR', 'UNKNOWN_FORMAT', 'VALIDATION_ERROR')),
    error_code VARCHAR(100) NOT NULL,
    error_message TEXT NOT NULL,
    stack_trace TEXT,
    context JSONB,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for parser_error table
CREATE INDEX idx_parser_error_ingestion ON parser_error(ingestion_id);
CREATE INDEX idx_parser_error_tenant ON parser_error(tenant_id);
CREATE INDEX idx_parser_error_category ON parser_error(error_category);
CREATE INDEX idx_parser_error_occurred_at ON parser_error(occurred_at);

-- Enable Row Level Security on parser_error table
ALTER TABLE parser_error ENABLE ROW LEVEL SECURITY;

-- Create policy for tenant isolation on parser_error
CREATE POLICY parser_error_tenant_isolation ON parser_error
    USING (tenant_id = current_setting('asp.tenant_id', true)::uuid);

