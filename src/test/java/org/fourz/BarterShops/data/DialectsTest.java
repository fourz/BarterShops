package org.fourz.BarterShops.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One predicate for the whole plugin — the drift it replaces is described in {@link Dialects} (PR #16). */
class DialectsTest {

    @Test
    void mysqlFamilyIncludesMariaDb() {
        assertTrue(Dialects.isMySql("mysql"));
        assertTrue(Dialects.isMySql("MySQL"), "RVNKCore's provider returns this exact casing");
        assertTrue(Dialects.isMySql("mariadb"), "the production server is MariaDB 10.6");
        assertTrue(Dialects.isMySql("MariaDB"));
    }

    @Test
    void everythingElseIsNotMySql() {
        assertFalse(Dialects.isMySql("sqlite"));
        assertFalse(Dialects.isMySql("SQLite"));
        assertFalse(Dialects.isMySql("unknown"), "SharedPoolDelegate returns this with no pool bound");
        assertFalse(Dialects.isMySql(null));
        assertFalse(Dialects.isMySql(""));
    }
}
