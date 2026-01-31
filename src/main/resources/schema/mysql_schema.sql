-- ============================================================
-- BarterShops MySQL Schema v1.0
-- RVNKCore Integration compliant
-- ============================================================

-- Main shops table
CREATE TABLE IF NOT EXISTS bs_shops (
    shop_id INT AUTO_INCREMENT PRIMARY KEY,
    owner_uuid CHAR(36) NOT NULL,
    shop_name VARCHAR(64),
    shop_type ENUM('BARTER', 'SELL', 'BUY', 'ADMIN') NOT NULL DEFAULT 'BARTER',

    -- Sign location
    sign_world VARCHAR(64) NOT NULL,
    sign_x DOUBLE NOT NULL,
    sign_y DOUBLE NOT NULL,
    sign_z DOUBLE NOT NULL,

    -- Chest location
    chest_world VARCHAR(64),
    chest_x DOUBLE,
    chest_y DOUBLE,
    chest_z DOUBLE,

    -- Status
    is_active TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_modified TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Indexes
    INDEX idx_owner (owner_uuid),
    INDEX idx_sign_location (sign_world, sign_x, sign_y, sign_z),
    INDEX idx_active (is_active),
    INDEX idx_type (shop_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Trade items (what the shop offers/accepts)
CREATE TABLE IF NOT EXISTS bs_trade_items (
    trade_item_id INT AUTO_INCREMENT PRIMARY KEY,
    shop_id INT NOT NULL,
    item_stack_data TEXT NOT NULL,
    currency_material VARCHAR(64),
    price_amount INT NOT NULL DEFAULT 0,
    stock_quantity INT NOT NULL DEFAULT 0,
    is_offering TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign key
    CONSTRAINT fk_trade_shop FOREIGN KEY (shop_id)
        REFERENCES bs_shops(shop_id) ON DELETE CASCADE,

    -- Indexes
    INDEX idx_shop (shop_id),
    INDEX idx_offering (is_offering)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Trade history (completed transactions)
CREATE TABLE IF NOT EXISTS bs_trade_history (
    transaction_id VARCHAR(36) PRIMARY KEY,
    shop_id INT NOT NULL,
    buyer_uuid CHAR(36) NOT NULL,
    seller_uuid CHAR(36) NOT NULL,
    item_stack_data TEXT NOT NULL,
    quantity INT NOT NULL,
    currency_material VARCHAR(64),
    price_paid INT NOT NULL DEFAULT 0,
    status ENUM('COMPLETED', 'CANCELLED', 'FAILED', 'PENDING', 'REFUNDED') NOT NULL DEFAULT 'COMPLETED',
    completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Indexes
    INDEX idx_shop (shop_id),
    INDEX idx_buyer (buyer_uuid),
    INDEX idx_seller (seller_uuid),
    INDEX idx_completed (completed_at),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Shop metadata (key-value pairs for extensibility)
CREATE TABLE IF NOT EXISTS bs_shop_metadata (
    shop_id INT NOT NULL,
    meta_key VARCHAR(64) NOT NULL,
    meta_value TEXT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Composite primary key
    PRIMARY KEY (shop_id, meta_key),

    -- Foreign key
    CONSTRAINT fk_meta_shop FOREIGN KEY (shop_id)
        REFERENCES bs_shops(shop_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Trade history archive (for old records)
CREATE TABLE IF NOT EXISTS bs_trade_history_archive (
    transaction_id VARCHAR(36) PRIMARY KEY,
    shop_id INT NOT NULL,
    buyer_uuid CHAR(36) NOT NULL,
    seller_uuid CHAR(36) NOT NULL,
    item_stack_data TEXT NOT NULL,
    quantity INT NOT NULL,
    currency_material VARCHAR(64),
    price_paid INT NOT NULL DEFAULT 0,
    status ENUM('COMPLETED', 'CANCELLED', 'FAILED', 'PENDING', 'REFUNDED') NOT NULL,
    completed_at TIMESTAMP NOT NULL,
    archived_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Indexes
    INDEX idx_archived (archived_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
