-- ============================================================
-- BarterShops SQLite Schema v1.0
-- RVNKCore Integration compliant
-- ============================================================

-- Main shops table
CREATE TABLE IF NOT EXISTS bs_shops (
    shop_id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_uuid TEXT NOT NULL,
    shop_name TEXT,
    shop_type TEXT NOT NULL DEFAULT 'BARTER' CHECK(shop_type IN ('BARTER', 'SELL', 'BUY', 'ADMIN')),

    -- Sign location
    sign_world TEXT NOT NULL,
    sign_x REAL NOT NULL,
    sign_y REAL NOT NULL,
    sign_z REAL NOT NULL,

    -- Chest location
    chest_world TEXT,
    chest_x REAL,
    chest_y REAL,
    chest_z REAL,

    -- Status
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    last_modified TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Indexes for shops
CREATE INDEX IF NOT EXISTS idx_bs_shops_owner ON bs_shops(owner_uuid);
CREATE INDEX IF NOT EXISTS idx_bs_shops_sign_loc ON bs_shops(sign_world, sign_x, sign_y, sign_z);
CREATE INDEX IF NOT EXISTS idx_bs_shops_active ON bs_shops(is_active);
CREATE INDEX IF NOT EXISTS idx_bs_shops_type ON bs_shops(shop_type);

-- Trade items (what the shop offers/accepts)
CREATE TABLE IF NOT EXISTS bs_trade_items (
    trade_item_id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id INTEGER NOT NULL,
    item_stack_data TEXT NOT NULL,
    currency_material TEXT,
    price_amount INTEGER NOT NULL DEFAULT 0,
    stock_quantity INTEGER NOT NULL DEFAULT 0,
    is_offering INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),

    FOREIGN KEY (shop_id) REFERENCES bs_shops(shop_id) ON DELETE CASCADE
);

-- Indexes for trade items
CREATE INDEX IF NOT EXISTS idx_bs_trade_items_shop ON bs_trade_items(shop_id);
CREATE INDEX IF NOT EXISTS idx_bs_trade_items_offering ON bs_trade_items(is_offering);

-- Trade history (completed transactions)
CREATE TABLE IF NOT EXISTS bs_trade_history (
    transaction_id TEXT PRIMARY KEY,
    shop_id INTEGER NOT NULL,
    buyer_uuid TEXT NOT NULL,
    seller_uuid TEXT NOT NULL,
    item_stack_data TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    currency_material TEXT,
    price_paid INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'COMPLETED' CHECK(status IN ('COMPLETED', 'CANCELLED', 'FAILED', 'PENDING', 'REFUNDED')),
    completed_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Indexes for trade history
CREATE INDEX IF NOT EXISTS idx_bs_history_shop ON bs_trade_history(shop_id);
CREATE INDEX IF NOT EXISTS idx_bs_history_buyer ON bs_trade_history(buyer_uuid);
CREATE INDEX IF NOT EXISTS idx_bs_history_seller ON bs_trade_history(seller_uuid);
CREATE INDEX IF NOT EXISTS idx_bs_history_completed ON bs_trade_history(completed_at);
CREATE INDEX IF NOT EXISTS idx_bs_history_status ON bs_trade_history(status);

-- Shop metadata (key-value pairs for extensibility)
CREATE TABLE IF NOT EXISTS bs_shop_metadata (
    shop_id INTEGER NOT NULL,
    meta_key TEXT NOT NULL,
    meta_value TEXT,
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),

    PRIMARY KEY (shop_id, meta_key),
    FOREIGN KEY (shop_id) REFERENCES bs_shops(shop_id) ON DELETE CASCADE
);

-- Trade history archive (for old records)
CREATE TABLE IF NOT EXISTS bs_trade_history_archive (
    transaction_id TEXT PRIMARY KEY,
    shop_id INTEGER NOT NULL,
    buyer_uuid TEXT NOT NULL,
    seller_uuid TEXT NOT NULL,
    item_stack_data TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    currency_material TEXT,
    price_paid INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL CHECK(status IN ('COMPLETED', 'CANCELLED', 'FAILED', 'PENDING', 'REFUNDED')),
    completed_at TEXT NOT NULL,
    archived_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Index for archive
CREATE INDEX IF NOT EXISTS idx_bs_archive_archived ON bs_trade_history_archive(archived_at);

-- Trigger to update last_modified on shops
CREATE TRIGGER IF NOT EXISTS trg_bs_shops_modified
    AFTER UPDATE ON bs_shops
    FOR EACH ROW
BEGIN
    UPDATE bs_shops SET last_modified = datetime('now') WHERE shop_id = NEW.shop_id;
END;
