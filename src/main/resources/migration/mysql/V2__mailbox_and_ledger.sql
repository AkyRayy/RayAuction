CREATE TABLE IF NOT EXISTS rayauction_ledger (
    player_id VARCHAR(36) NOT NULL,
    currency VARCHAR(32) NOT NULL,
    balance DECIMAL(20, 4) NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (player_id, currency)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rayauction_mailbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    player_id VARCHAR(36) NOT NULL,
    reason VARCHAR(32) NOT NULL,
    item_data LONGBLOB NULL,
    item_material VARCHAR(64) NULL,
    item_amount INT NOT NULL DEFAULT 1,
    amount DECIMAL(20, 4) NULL,
    currency VARCHAR(32) NULL,
    auction_id BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    delivered TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_mailbox_pending (player_id, delivered, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS rayauction_sync_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_type VARCHAR(32) NOT NULL,
    payload TEXT NOT NULL,
    server_id VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL,
    processed TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_sync_unprocessed (processed, id),
    KEY idx_sync_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
