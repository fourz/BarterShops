package org.fourz.BarterShops.sign;

import org.fourz.BarterShops.data.dto.ShopDataDTO.ShopType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Sign type round-trips through shop_type, and ADMIN rows stay ADMIN (#2118). */
class SignTypeTest {

    @Test
    void persistedTypeRestoresTheSignType() {
        for (SignType type : SignType.values()) {
            assertEquals(type, SignType.fromShopType(type.toShopType(ShopType.BARTER)));
        }
    }

    @Test
    void adminRowKeepsAdminAndRunsAsBarter() {
        assertEquals(ShopType.ADMIN, SignType.SELL.toShopType(ShopType.ADMIN));
        assertEquals(SignType.BARTER, SignType.fromShopType(ShopType.ADMIN));
        assertEquals(SignType.BARTER, SignType.fromShopType(null));
    }
}
