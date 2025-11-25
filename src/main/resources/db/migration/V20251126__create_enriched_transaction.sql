-- Create enriched_transaction table for downstream enrichment pipeline
CREATE TABLE enriched_transaction (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    normalized_transaction_id UUID NOT NULL REFERENCES normalized_transaction(transaction_id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenant(id) ON DELETE CASCADE,
    bank_connection_id UUID NOT NULL REFERENCES bank_connection(id) ON DELETE CASCADE,
    user_ref VARCHAR(255),
    txn_date DATE NOT NULL,
    description_raw TEXT NOT NULL,
    description_clean TEXT,
    category VARCHAR(100),
    subcategory VARCHAR(100),
    merchant_name VARCHAR(255),
    merchant_normalized VARCHAR(255),
    is_salary BOOLEAN NOT NULL DEFAULT FALSE,
    is_recurring BOOLEAN NOT NULL DEFAULT FALSE,
    recurring_group_id UUID,
    direction VARCHAR(10) NOT NULL,
    amount NUMERIC(18,2) NOT NULL,
    amount_abs NUMERIC(18,2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_enriched_txn_tenant ON enriched_transaction(tenant_id);
CREATE INDEX idx_enriched_txn_normalized ON enriched_transaction(normalized_transaction_id);
CREATE INDEX idx_enriched_txn_date ON enriched_transaction(txn_date);
CREATE INDEX idx_enriched_txn_merchant ON enriched_transaction(merchant_name);

