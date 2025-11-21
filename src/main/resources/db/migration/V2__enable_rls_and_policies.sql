-- Enable Row Level Security on api_key table
ALTER TABLE api_key ENABLE ROW LEVEL SECURITY;

-- Create policy for tenant isolation on api_key
-- This policy ensures tenants can only see their own API keys
CREATE POLICY api_key_tenant_isolation ON api_key
    USING (tenant_id = current_setting('asp.tenant_id', true)::uuid);

-- Note: For production, you may want to add additional RLS policies
-- for other tables that need tenant isolation
-- Example:
-- ALTER TABLE your_table ENABLE ROW LEVEL SECURITY;
-- CREATE POLICY your_table_tenant_isolation ON your_table
--     USING (tenant_id = current_setting('asp.tenant_id', true)::uuid);

