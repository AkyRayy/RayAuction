CREATE INDEX IF NOT EXISTS idx_auctions_status_price ON rayauction_auctions (status, price);
CREATE INDEX IF NOT EXISTS idx_auctions_status_created ON rayauction_auctions (status, created_at);
CREATE INDEX IF NOT EXISTS idx_auctions_seller_created ON rayauction_auctions (seller_id, created_at);
CREATE INDEX IF NOT EXISTS idx_transactions_currency ON rayauction_transactions (currency);
CREATE INDEX IF NOT EXISTS idx_ledger_currency ON rayauction_ledger (currency);
