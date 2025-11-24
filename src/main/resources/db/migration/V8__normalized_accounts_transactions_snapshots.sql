-- Create normalized_account table
CREATE TABLE normalized_account (
    account_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ingestion_id UUID NOT NULL REFERENCES ingestion(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    user_ref VARCHAR NOT NULL,
    bank_connection_id UUID NOT NULL REFERENCES bank_connection(id) ON DELETE CASCADE,
    account_number VARCHAR NOT NULL,
    iban VARCHAR,
    account_type VARCHAR NOT NULL,  -- CHECKING, SAVINGS, etc.
    currency VARCHAR(3) NOT NULL DEFAULT 'MAD',
    balance DECIMAL(15, 2),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Ensure unique account per tenant/user/bank connection
    UNIQUE(tenant_id, user_ref, account_number, bank_connection_id)
);

-- Create indexes for normalized_account
CREATE INDEX idx_normalized_account_tenant_id ON normalized_account(tenant_id);
CREATE INDEX idx_normalized_account_user_ref ON normalized_account(user_ref);
CREATE INDEX idx_normalized_account_ingestion_id ON normalized_account(ingestion_id);
CREATE INDEX idx_normalized_account_bank_connection_id ON normalized_account(bank_connection_id);
CREATE INDEX idx_normalized_account_tenant_user_ref ON normalized_account(tenant_id, user_ref);

-- Create normalized_transaction table
CREATE TABLE normalized_transaction (
    transaction_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ingestion_id UUID NOT NULL REFERENCES ingestion(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES normalized_account(account_id) ON DELETE CASCADE,
    user_ref VARCHAR NOT NULL,
    date DATE NOT NULL,
    description TEXT,
    amount DECIMAL(15, 2) NOT NULL,  -- Signed: positive = credit, negative = debit
    currency VARCHAR(3) NOT NULL DEFAULT 'MAD',
    category VARCHAR,
    merchant VARCHAR,
    raw_description TEXT,  -- Original description from bank statement
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Idempotency: prevent duplicate transactions
    UNIQUE(account_id, ingestion_id, date, amount, raw_description)
);

-- Create indexes for normalized_transaction
CREATE INDEX idx_normalized_transaction_tenant_id ON normalized_transaction(tenant_id);
CREATE INDEX idx_normalized_transaction_user_ref ON normalized_transaction(user_ref);
CREATE INDEX idx_normalized_transaction_ingestion_id ON normalized_transaction(ingestion_id);
CREATE INDEX idx_normalized_transaction_account_id ON normalized_transaction(account_id);
CREATE INDEX idx_normalized_transaction_date ON normalized_transaction(date);
CREATE INDEX idx_normalized_transaction_account_date ON normalized_transaction(account_id, date);
CREATE INDEX idx_normalized_transaction_tenant_user_ref ON normalized_transaction(tenant_id, user_ref);

-- Create normalized_account_snapshot table
CREATE TABLE normalized_account_snapshot (
    snapshot_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ingestion_id UUID NOT NULL REFERENCES ingestion(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES normalized_account(account_id) ON DELETE CASCADE,
    user_ref VARCHAR NOT NULL,
    balance DECIMAL(15, 2) NOT NULL,
    date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- One snapshot per account per ingestion
    UNIQUE(account_id, ingestion_id)
);

-- Create indexes for normalized_account_snapshot
CREATE INDEX idx_normalized_account_snapshot_tenant_id ON normalized_account_snapshot(tenant_id);
CREATE INDEX idx_normalized_account_snapshot_user_ref ON normalized_account_snapshot(user_ref);
CREATE INDEX idx_normalized_account_snapshot_ingestion_id ON normalized_account_snapshot(ingestion_id);
CREATE INDEX idx_normalized_account_snapshot_account_id ON normalized_account_snapshot(account_id);
CREATE INDEX idx_normalized_account_snapshot_date ON normalized_account_snapshot(date);
CREATE INDEX idx_normalized_account_snapshot_tenant_user_ref ON normalized_account_snapshot(tenant_id, user_ref);

-- Enable Row Level Security on all tables
ALTER TABLE normalized_account ENABLE ROW LEVEL SECURITY;
ALTER TABLE normalized_transaction ENABLE ROW LEVEL SECURITY;
ALTER TABLE normalized_account_snapshot ENABLE ROW LEVEL SECURITY;

-- Create RLS policies for tenant isolation on normalized_account
CREATE POLICY normalized_account_tenant_isolation ON normalized_account
    USING (tenant_id = current_setting('asp.tenant_id', true)::uuid);

-- Create RLS policies for tenant isolation on normalized_transaction
CREATE POLICY normalized_transaction_tenant_isolation ON normalized_transaction
    USING (tenant_id = current_setting('asp.tenant_id', true)::uuid);

-- Create RLS policies for tenant isolation on normalized_account_snapshot
CREATE POLICY normalized_account_snapshot_tenant_isolation ON normalized_account_snapshot
    USING (tenant_id = current_setting('asp.tenant_id', true)::uuid);

