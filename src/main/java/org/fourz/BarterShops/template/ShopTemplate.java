package org.fourz.BarterShops.template;

import org.bukkit.Material;
import org.fourz.BarterShops.sign.SignType;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Shop template data model.
 * Stores reusable shop configurations for quick setup.
 *
 * @param id Template unique identifier
 * @param name Template display name
 * @param owner UUID of template creator (null for server-wide presets)
 * @param signType Type of shop (STACKABLE, UNSTACKABLE)
 * @param description Optional template description
 * @param category Template category for organization
 * @param tags Comma-separated tags for filtering
 * @param createdAt Creation timestamp
 * @param isServerPreset True if admin-defined server-wide template
 */
public record ShopTemplate(
    String id,
    String name,
    UUID owner,
    SignType signType,
    String description,
    String category,
    String tags,
    LocalDateTime createdAt,
    boolean isServerPreset
) {
    /**
     * Creates a new builder for ShopTemplate.
     *
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder pattern for creating ShopTemplate instances.
     */
    public static class Builder {
        private String id;
        private String name;
        private UUID owner;
        private SignType signType = SignType.STACKABLE;
        private String description = "";
        private String category = "general";
        private String tags = "";
        private LocalDateTime createdAt = LocalDateTime.now();
        private boolean isServerPreset = false;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder owner(UUID owner) {
            this.owner = owner;
            return this;
        }

        public Builder signType(SignType signType) {
            this.signType = signType;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder tags(String tags) {
            this.tags = tags;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder isServerPreset(boolean isServerPreset) {
            this.isServerPreset = isServerPreset;
            return this;
        }

        public ShopTemplate build() {
            if (id == null || name == null) {
                throw new IllegalStateException("Template id and name are required");
            }
            return new ShopTemplate(id, name, owner, signType, description, category, tags, createdAt, isServerPreset);
        }
    }

    /**
     * Checks if the template is owned by the given player.
     *
     * @param playerId Player UUID to check
     * @return true if player owns this template
     */
    public boolean isOwnedBy(UUID playerId) {
        return owner != null && owner.equals(playerId);
    }

    /**
     * Checks if the template matches any of the given tags.
     *
     * @param searchTags Tags to search for (comma-separated)
     * @return true if any tag matches
     */
    public boolean matchesTags(String searchTags) {
        if (tags == null || tags.isEmpty() || searchTags == null || searchTags.isEmpty()) {
            return false;
        }

        String[] templateTags = tags.toLowerCase().split(",");
        String[] searchTagsArray = searchTags.toLowerCase().split(",");

        for (String searchTag : searchTagsArray) {
            for (String templateTag : templateTags) {
                if (templateTag.trim().equals(searchTag.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Creates a copy of this template with a new owner.
     *
     * @param newOwner New owner UUID
     * @return Copied template
     */
    public ShopTemplate copyForOwner(UUID newOwner) {
        return ShopTemplate.builder()
            .id(UUID.randomUUID().toString())
            .name(name)
            .owner(newOwner)
            .signType(signType)
            .description(description)
            .category(category)
            .tags(tags)
            .createdAt(LocalDateTime.now())
            .isServerPreset(false)
            .build();
    }
}
