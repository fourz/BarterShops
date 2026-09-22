package org.fourz.BarterShops.data;

/**
 * One place that decides whether a pool's reported type means "MySQL-family SQL".
 *
 * <p>The predicate used to be copy-pasted per repository, and they drifted: three classes accepted
 * {@code mariadb} while {@code TradeRepositoryImpl} compared against {@code mysql} exactly, so the
 * same pool could be treated as MySQL in one query and SQLite in the next — and the summary
 * roll-ups would have sent {@code INSERT OR REPLACE} to a MariaDB server (PR #16 review).</p>
 *
 * <p>Not reachable today: RVNKCore's provider returns the literal {@code "MySQL"} and never the
 * server's real flavour. It is a live trap all the same — the production database <b>is</b> MariaDB
 * (10.6), so the day any provider starts reporting what it actually connected to, the strict
 * comparison silently picks the wrong dialect on a production server.</p>
 */
public final class Dialects {

    private Dialects() {
    }

    /** True when {@code type} is a MySQL-family server (MySQL or MariaDB); false for SQLite/null. */
    public static boolean isMySql(String type) {
        return type != null && (type.equalsIgnoreCase("mysql") || type.equalsIgnoreCase("mariadb"));
    }
}
