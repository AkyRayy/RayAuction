CREATE TABLE IF NOT EXISTS rayauction_auctions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    seller_id VARCHAR(36) NOT NULL,
    seller_name VARCHAR(16) NOT NULL,
    item_data LONGBLOB NOT NULL,
    item_material VARCHAR(64) NOT NULL,
    item_name VARCHAR(512) NOT NULL,
    item_lore TEXT NOT NULL,
    item_category VARCHAR(16) NOT NULL DEFAULT 'other',
    item_amount INT NOT NULL DEFAULT 1,
    price DECIMAL(20, 4) NOT NULL,
    currency VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    buyer_id VARCHAR(36) NULL,
    version INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_auctions_status_expires (status, expires_at),
    KEY idx_auctions_seller (seller_id, status),
    KEY idx_auctions_currency (currency, status),
    KEY idx_auctions_material (item_material),
    KEY idx_auctions_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rayauction_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    auction_id BIGINT NOT NULL,
    buyer_id VARCHAR(36) NOT NULL,
    seller_id VARCHAR(36) NOT NULL,
    seller_name VARCHAR(16) NOT NULL,
    buyer_name VARCHAR(16) NOT NULL,
    item_data LONGBLOB NOT NULL,
    item_material VARCHAR(64) NOT NULL,
    item_amount INT NOT NULL DEFAULT 1,
    price DECIMAL(20, 4) NOT NULL,
    currency VARCHAR(32) NOT NULL,
    tax_amount DECIMAL(20, 4) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_transaction_auction (auction_id),
    KEY idx_transactions_buyer (buyer_id, created_at),
    KEY idx_transactions_seller (seller_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rayauction_limits (
    player_id VARCHAR(36) NOT NULL,
    custom_limit INT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (player_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
