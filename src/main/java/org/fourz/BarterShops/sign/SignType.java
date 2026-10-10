package org.fourz.BarterShops.sign;

import org.fourz.BarterShops.data.dto.ShopDataDTO.ShopType;

public enum SignType {
    BARTER,
    SELL,
    BUY;

    /**
     * Sign type for a persisted shop_type. ADMIN is a server-managed row, not a sign mode, so it
     * runs as a BARTER sign (admin behaviour comes from shop_config_is_admin).
     */
    public static SignType fromShopType(ShopType shopType) {
        if (shopType == null) return BARTER;
        return switch (shopType) {
            case SELL -> SELL;
            case BUY -> BUY;
            case BARTER, ADMIN -> BARTER;
        };
    }

    /**
     * shop_type to persist for this sign type. An ADMIN row keeps ADMIN: cycling the sign
     * must not demote a server-managed shop (#2118).
     */
    public ShopType toShopType(ShopType existing) {
        if (existing == ShopType.ADMIN) return ShopType.ADMIN;
        return switch (this) {
            case BARTER -> ShopType.BARTER;
            case SELL -> ShopType.SELL;
            case BUY -> ShopType.BUY;
        };
    }
}
