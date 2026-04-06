package org.fourz.BarterShops.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Reusable table display utility for command output.
 *
 * <p>Uses color-coded segments rather than fixed-width padding so output renders
 * correctly in Minecraft's variable-width chat font. Each column gets a distinct
 * {@link ChatColor}; columns are separated by a two-space gap.</p>
 *
 * <pre>{@code
 * TableDisplay<ShopDataDTO> table = TableDisplay.<ShopDataDTO>builder()
 *     .column("#",        ChatColor.GRAY,      s -> String.valueOf(s.shopId()))
 *     .column("TYPE",     ChatColor.YELLOW,    s -> s.shopType().name())
 *     .column("Offering", ChatColor.GREEN,     s -> formatOffering(s))
 *     .column("Owner",    ChatColor.WHITE,     s -> ownerName(s))
 *     .column("Location", ChatColor.DARK_GRAY, s -> coords(s))
 *     .build();
 *
 * table.renderHeader(sender);
 * table.render(sender, pageItems);
 * }</pre>
 */
public class TableDisplay<T> {

    private static final String COL_SEP = "  ";

    private final List<Column<T>> columns;

    private TableDisplay(List<Column<T>> columns) {
        this.columns = columns;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Sends a header row with each column name rendered in its column colour.
     */
    public void renderHeader(CommandSender sender) {
        sender.sendMessage(buildLine(col -> col.header()));
    }

    /**
     * Sends one formatted line per item in {@code items}.
     */
    public void render(CommandSender sender, List<T> items) {
        for (T item : items) {
            sender.sendMessage(buildLine(col -> col.extractor().apply(item)));
        }
    }

    private String buildLine(Function<Column<T>, String> valueFor) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            Column<T> col = columns.get(i);
            if (i > 0) sb.append(ChatColor.RESET).append(COL_SEP);
            sb.append(col.color()).append(valueFor.apply(col));
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------

    public static class Builder<T> {
        private final List<Column<T>> columns = new ArrayList<>();

        /**
         * Adds a column.
         *
         * @param header    Column header label
         * @param color     {@link ChatColor} applied to this column's value
         * @param extractor Function that extracts the display string from a row item
         */
        public Builder<T> column(String header, ChatColor color, Function<T, String> extractor) {
            columns.add(new Column<>(header, color, extractor));
            return this;
        }

        public TableDisplay<T> build() {
            if (columns.isEmpty()) throw new IllegalStateException("TableDisplay requires at least one column");
            return new TableDisplay<>(List.copyOf(columns));
        }
    }

    // -------------------------------------------------------------------------

    private record Column<T>(String header, ChatColor color, Function<T, String> extractor) {}
}
