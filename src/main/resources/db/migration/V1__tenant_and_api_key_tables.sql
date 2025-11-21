-- Create tenant table
CREATE TABLE tenant (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL UNIQUE,
    slug VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    plan VARCHAR(50) NOT NULL CHECK (plan IN ('FREE', 'PRO', 'ENTERPRISE')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for tenant table
CREATE INDEX idx_tenant_slug ON tenant(slug);
CREATE INDEX idx_tenant_status ON tenant(status);

-- Create api_key table
CREATE TABLE api_key (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    prefix VARCHAR(20) NOT NULL UNIQUE,
    key_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    sandbox BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP WITH TIME ZONE
);

-- Create indexes for api_key table
CREATE INDEX idx_api_key_tenant_id ON api_key(tenant_id);
CREATE INDEX idx_api_key_prefix ON api_key(prefix);
CREATE INDEX idx_api_key_sandbox ON api_key(sandbox);

-- Create api_key_scope table for many-to-many relationship
CREATE TABLE api_key_scope (
    api_key_id UUID NOT NULL REFERENCES api_key(id) ON DELETE CASCADE,
    scope VARCHAR(255) NOT NULL,
    PRIMARY KEY (api_key_id, scope)
);

-- Create index for api_key_scope
CREATE INDEX idx_api_key_scope_api_key_id ON api_key_scope(api_key_id);

