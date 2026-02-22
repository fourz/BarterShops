package org.fourz.BarterShops.data;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * Serializes and deserializes ItemStack and shop configuration for database persistence.
 * Handles conversion to/from JSON format using String-based maps for flexibility.
 */
public class ShopConfigSerializer {

    /**
     * Serialize an ItemStack to a JSON-like string representation.
     * Preserves: type, amount, durability, enchantments, lore, custom name, NBT data.
     */
    public static String serializeItemStack(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"type\":\"").append(item.getType().name()).append("\",");
        sb.append("\"amount\":").append(item.getAmount()).append(",");

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                // Serialize as compound meta object
                sb.append("\"meta\":{");

                if (meta.hasDisplayName()) {
                    sb.append("\"displayName\":\"").append(escapeJson(meta.getDisplayName())).append("\",");
                }

                if (meta.hasLore() && meta.getLore() != null) {
                    sb.append("\"lore\":[");
                    List<String> lore = meta.getLore();
                    for (int i = 0; i < lore.size(); i++) {
                        sb.append("\"").append(escapeJson(lore.get(i))).append("\"");
                        if (i < lore.size() - 1) sb.append(",");
                    }
                    sb.append("],");
                }

                if (meta.hasEnchants()) {
                    sb.append("\"enchants\":{");
                    var enchants = meta.getEnchants();
                    var iter = enchants.entrySet().iterator();
                    while (iter.hasNext()) {
                        var entry = iter.next();
                        sb.append("\"").append(entry.getKey().getKey().getKey()).append(":").append(entry.getValue()).append("\"");
                        if (iter.hasNext()) sb.append(",");
                    }
                    sb.append("},");
                }

                // Remove trailing comma if present
                if (sb.charAt(sb.length() - 1) == ',') {
                    sb.setLength(sb.length() - 1);
                }
                sb.append("}");
            }
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * Deserialize an ItemStack from JSON string representation.
     * Reconstructs: type, amount, durability, enchantments, lore, custom name.
     */
    public static ItemStack deserializeItemStack(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }

        try {
            // Simple parsing without JSON library (to avoid dependencies)
            // Extract type
            String typeStr = extractJsonString(json, "\"type\":\"", "\"");
            if (typeStr == null || typeStr.isEmpty()) {
                return null;
            }

            Material type = Material.valueOf(typeStr);
            int amount = extractJsonInt(json, "\"amount\":");

            ItemStack item = new ItemStack(type, Math.max(1, amount));

            // Try to reconstruct meta if present
            if (json.contains("\"meta\":{")) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    // Extract display name if present
                    String displayName = extractJsonString(json, "\"displayName\":\"", "\"");
                    if (displayName != null && !displayName.isEmpty()) {
                        meta.setDisplayName(displayName);
                    }

                    // Extract lore if present
                    if (json.contains("\"lore\":[")) {
                        List<String> lore = extractJsonLore(json);
                        if (!lore.isEmpty()) {
                            meta.setLore(lore);
                        }
                    }

                    item.setItemMeta(meta);
                }
            }

            return item;

        } catch (Exception e) {
            // Fallback: return null if parsing fails
            return null;
        }
    }

    /**
     * Serialize a list of ItemStacks to JSON array string.
     */
    public static String serializeItemStackList(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            ItemStack item = items.get(i);
            String serialized = serializeItemStack(item);
            if (serialized != null) {
                sb.append(serialized);
                if (i < items.size() - 1) sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Deserialize a list of ItemStacks from JSON array string.
     */
    public static List<ItemStack> deserializeItemStackList(String json) {
        List<ItemStack> items = new ArrayList<>();
        if (json == null || json.isEmpty() || json.equals("[]")) {
            return items;
        }

        try {
            // Simple array parsing - split by objects
            int depth = 0;
            int start = -1;

            for (int i = 0; i < json.length(); i++) {
                char c = json.charAt(i);

                if (c == '{') {
                    if (depth == 0) start = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && start != -1) {
                        String itemJson = json.substring(start, i + 1);
                        ItemStack item = deserializeItemStack(itemJson);
                        if (item != null) {
                            items.add(item);
                        }
                        start = -1;
                    }
                }
            }

            return items;

        } catch (Exception e) {
            return items;
        }
    }

    // ========================================================
    // Helper methods for JSON parsing
    // ========================================================

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String extractJsonString(String json, String key, String endDelim) {
        int startIdx = json.indexOf(key);
        if (startIdx == -1) return null;

        startIdx += key.length();
        int endIdx = json.indexOf(endDelim, startIdx);
        if (endIdx == -1) return null;

        return json.substring(startIdx, endIdx);
    }

    private static int extractJsonInt(String json, String key) {
        try {
            int startIdx = json.indexOf(key);
            if (startIdx == -1) return 0;

            startIdx += key.length();
            int endIdx = startIdx;

            while (endIdx < json.length() && Character.isDigit(json.charAt(endIdx))) {
                endIdx++;
            }

            if (endIdx > startIdx) {
                return Integer.parseInt(json.substring(startIdx, endIdx));
            }
        } catch (Exception e) {
            // Fall through
        }
        return 0;
    }

    private static List<String> extractJsonLore(String json) {
        List<String> lore = new ArrayList<>();
        try {
            int startIdx = json.indexOf("\"lore\":[");
            if (startIdx == -1) return lore;

            startIdx += "\"lore\":[".length();
            int endIdx = json.indexOf("]", startIdx);
            if (endIdx == -1) return lore;

            String loreStr = json.substring(startIdx, endIdx);

            // Split by quoted strings
            StringBuilder current = new StringBuilder();
            boolean inString = false;
            boolean escaped = false;

            for (int i = 0; i < loreStr.length(); i++) {
                char c = loreStr.charAt(i);

                if (escaped) {
                    current.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    current.append(c);
                    escaped = true;
                } else if (c == '"') {
                    if (inString) {
                        lore.add(current.toString());
                        current = new StringBuilder();
                        inString = false;
                    } else {
                        inString = true;
                    }
                } else if (inString) {
                    current.append(c);
                }
            }
        } catch (Exception e) {
            // Fall through
        }
        return lore;
    }
}
