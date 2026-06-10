-- ============================================================
-- BarterShops SQLite Schema
-- Table names use the configured prefix (config.yml → database.tablePrefix).
-- Default prefix: "barter_"  →  barter_shops, barter_trade_records, etc.
-- The "barter_" prefix below reflects the Ravenkraft default install.
-- Replace the prefix to match your tablePrefix setting.
-- ============================================================

CREATE TABLE IF NOT EXISTS barter_shops (
    shop_id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_uuid TEXT NOT NULL,
    shop_name TEXT,
    shop_type TEXT NOT NULL DEFAULT 'BARTER' CHECK(shop_type IN ('BARTER', 'SELL', 'BUY')),

    -- Sign location
    location_world TEXT NOT NULL,
    location_x REAL NOT NULL,
    location_y REAL NOT NULL,
    location_z REAL NOT NULL,

    -- Chest location
    chest_location_world TEXT,
    chest_location_x REAL,
    chest_location_y REAL,
    chest_location_z REAL,

    -- Group membership (NULL = ungrouped)
    group_id INTEGER NULL,

    -- Status
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    last_modified TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_barter_shops_owner ON barter_shops(owner_uuid);
CREATE INDEX IF NOT EXISTS idx_barter_shops_location ON barter_shops(location_world, location_x, location_y, location_z);
CREATE INDEX IF NOT EXISTS idx_barter_shops_active ON barter_shops(is_active);
CREATE INDEX IF NOT EXISTS idx_barter_shops_type ON barter_shops(shop_type);
CREATE INDEX IF NOT EXISTS idx_barter_shops_owner_active ON barter_shops(owner_uuid, is_active);
CREATE INDEX IF NOT EXISTS idx_barter_shops_group ON barter_shops(group_id);

-- Shop groups (owner-defined groupings of shops, per-world)
CREATE TABLE IF NOT EXISTS barter_shop_groups (
    group_id INTEGER PRIMARY KEY AUTOINCREMENT,
    group_name TEXT NOT NULL,
    owner_uuid TEXT NOT NULL,
    world TEXT NOT NULL,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    last_modified TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_barter_shop_groups_owner ON barter_shop_groups(owner_uuid);
CREATE INDEX IF NOT EXISTS idx_barter_shop_groups_world ON barter_shop_groups(world);
CREATE INDEX IF NOT EXISTS idx_barter_shop_groups_active ON barter_shop_groups(is_active);

-- Shop group members (co-owners per group)
CREATE TABLE IF NOT EXISTS barter_shop_group_members (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    group_id INTEGER NOT NULL,
    member_uuid TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'CO_OWNER',
    added_at TEXT NOT NULL DEFAULT (datetime('now')),

    UNIQUE(group_id, member_uuid),
    FOREIGN KEY (group_id) REFERENCES barter_shop_groups(group_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_barter_shop_group_members_member ON barter_shop_group_members(member_uuid);

-- Trade items (what the shop offers/accepts)
CREATE TABLE IF NOT EXISTS barter_trade_items (
    trade_item_id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id INTEGER NOT NULL,
    item_stack_data TEXT NOT NULL,
    currency_material TEXT,
    price_amount INTEGER NOT NULL DEFAULT 0,
    stock_quantity INTEGER NOT NULL DEFAULT 0,
    is_offering INTEGER NOT NULL DEFAULT 1,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),

    FOREIGN KEY (shop_id) REFERENCES barter_shops(shop_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_barter_trade_items_shop ON barter_trade_items(shop_id);
CREATE INDEX IF NOT EXISTS idx_barter_trade_items_offering ON barter_trade_items(is_offering);

-- Trade records (completed transactions)
CREATE TABLE IF NOT EXISTS barter_trade_records (
    transaction_id TEXT PRIMARY KEY,
    shop_id INTEGER NULL,
    buyer_uuid TEXT NOT NULL,
    seller_uuid TEXT NOT NULL,
    item_stack_data TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    item_type TEXT,
    currency_material TEXT,
    price_paid INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'COMPLETED' CHECK(status IN ('COMPLETED', 'CANCELLED', 'FAILED', 'PENDING', 'REFUNDED')),
    trade_source TEXT NOT NULL DEFAULT 'UNKNOWN',
    completed_at TEXT NOT NULL DEFAULT (datetime('now')),

    FOREIGN KEY (shop_id) REFERENCES barter_shops(shop_id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_barter_trade_records_shop ON barter_trade_records(shop_id);
CREATE INDEX IF NOT EXISTS idx_barter_trade_records_buyer ON barter_trade_records(buyer_uuid);
CREATE INDEX IF NOT EXISTS idx_barter_trade_records_seller ON barter_trade_records(seller_uuid);
CREATE INDEX IF NOT EXISTS idx_barter_trade_records_completed ON barter_trade_records(completed_at);
CREATE INDEX IF NOT EXISTS idx_barter_trade_records_status ON barter_trade_records(status);
CREATE INDEX IF NOT EXISTS idx_barter_trade_records_buyer_completed ON barter_trade_records(buyer_uuid, completed_at);

-- Shop metadata (key-value pairs for extensibility)
CREATE TABLE IF NOT EXISTS barter_shop_metadata (
    shop_id INTEGER NOT NULL,
    meta_key TEXT NOT NULL,
    meta_value TEXT,
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),

    PRIMARY KEY (shop_id, meta_key),
    FOREIGN KEY (shop_id) REFERENCES barter_shops(shop_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_barter_metadata_key ON barter_shop_metadata(meta_key);

-- Shop ratings and reviews
CREATE TABLE IF NOT EXISTS barter_shop_ratings (
    rating_id INTEGER PRIMARY KEY AUTOINCREMENT,
    shop_id INTEGER NOT NULL,
    rater_uuid TEXT NOT NULL,
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    review TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),

    UNIQUE(shop_id, rater_uuid),
    FOREIGN KEY (shop_id) REFERENCES barter_shops(shop_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_barter_shop_ratings_shop ON barter_shop_ratings(shop_id);
CREATE INDEX IF NOT EXISTS idx_barter_shop_ratings_rater ON barter_shop_ratings(rater_uuid);

-- Trade records archive (retention manager moves records older than N days here)
CREATE TABLE IF NOT EXISTS barter_trade_records_archive (
    transaction_id TEXT PRIMARY KEY,
    shop_id INTEGER NULL,
    buyer_uuid TEXT NOT NULL,
    seller_uuid TEXT NOT NULL,
    item_stack_data TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    item_type TEXT,
    currency_material TEXT,
    price_paid INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL CHECK(status IN ('COMPLETED', 'CANCELLED', 'FAILED', 'PENDING', 'REFUNDED')),
    trade_source TEXT NOT NULL DEFAULT 'UNKNOWN',
    completed_at TEXT NOT NULL,
    archived_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_barter_archive_archived ON barter_trade_records_archive(archived_at);

-- Trigger to update last_modified on shops
CREATE TRIGGER IF NOT EXISTS trg_barter_shops_modified
    AFTER UPDATE ON barter_shops
    FOR EACH ROW
BEGIN
    UPDATE barter_shops SET last_modified = datetime('now') WHERE shop_id = NEW.shop_id;
END;
