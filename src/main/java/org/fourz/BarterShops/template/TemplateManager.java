package org.fourz.BarterShops.template;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.fourz.BarterShops.BarterShops;
import org.fourz.BarterShops.sign.BarterSign;
import org.fourz.BarterShops.sign.SignType;
import org.fourz.rvnkcore.util.log.LogManager;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages shop templates - CRUD operations and persistence.
 * Templates are stored in templates.yml in the plugin data folder.
 */
public class TemplateManager {

    private final BarterShops plugin;
    private final LogManager logger;
    private final File templatesFile;
    private YamlConfiguration templatesConfig;
    private final Map<String, ShopTemplate> templates = new HashMap<>();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public TemplateManager(BarterShops plugin) {
        this.plugin = plugin;
        this.logger = LogManager.getInstance(plugin, "TemplateManager");
        this.templatesFile = new File(plugin.getDataFolder(), "templates.yml");
        initialize();
    }

    /**
     * Initializes the template manager.
     * Creates templates.yml if it doesn't exist and loads templates.
     */
    private void initialize() {
        logger.info("Initializing TemplateManager...");

        if (!templatesFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                templatesFile.createNewFile();
                logger.info("Created new templates.yml");
            } catch (IOException e) {
                logger.error("Failed to create templates.yml: " + e.getMessage());
                return;
            }
        }

        templatesConfig = YamlConfiguration.loadConfiguration(templatesFile);
        loadTemplates();
        logger.info("TemplateManager initialized with " + templates.size() + " templates");
    }

    /**
     * Loads templates from templates.yml.
     */
    private void loadTemplates() {
        templates.clear();

        if (!templatesConfig.contains("templates")) {
            logger.debug("No templates section found in templates.yml");
            return;
        }

        ConfigurationSection templatesSection = templatesConfig.getConfigurationSection("templates");
        if (templatesSection == null) {
            return;
        }

        for (String key : templatesSection.getKeys(false)) {
            ConfigurationSection templateSection = templatesSection.getConfigurationSection(key);
            if (templateSection == null) {
                continue;
            }

            try {
                ShopTemplate template = deserializeTemplate(key, templateSection);
                templates.put(template.id(), template);
                logger.debug("Loaded template: " + template.name());
            } catch (Exception e) {
                logger.warning("Failed to load template " + key + ": " + e.getMessage());
            }
        }

        logger.info("Loaded " + templates.size() + " templates from storage");
    }

    /**
     * Deserializes a template from YAML configuration.
     *
     * @param id Template ID
     * @param section Configuration section
     * @return Deserialized ShopTemplate
     */
    private ShopTemplate deserializeTemplate(String id, ConfigurationSection section) {
        String ownerStr = section.getString("owner");
        UUID owner = ownerStr != null && !ownerStr.equals("SERVER") ? UUID.fromString(ownerStr) : null;

        SignType signType = SignType.valueOf(section.getString("signType", "STACKABLE"));
        LocalDateTime createdAt = LocalDateTime.parse(
            section.getString("createdAt", LocalDateTime.now().format(DATE_FORMATTER)),
            DATE_FORMATTER
        );

        return ShopTemplate.builder()
            .id(id)
            .name(section.getString("name", "Unnamed Template"))
            .owner(owner)
            .signType(signType)
            .description(section.getString("description", ""))
            .category(section.getString("category", "general"))
            .tags(section.getString("tags", ""))
            .createdAt(createdAt)
            .isServerPreset(section.getBoolean("isServerPreset", false))
            .build();
    }

    /**
     * Saves a template to storage.
     *
     * @param template Template to save
     * @return true if saved successfully
     */
    public boolean saveTemplate(ShopTemplate template) {
        try {
            ConfigurationSection templatesSection = templatesConfig.getConfigurationSection("templates");
            if (templatesSection == null) {
                templatesSection = templatesConfig.createSection("templates");
            }

            ConfigurationSection templateSection = templatesSection.createSection(template.id());
            serializeTemplate(template, templateSection);

            templatesConfig.save(templatesFile);
            templates.put(template.id(), template);

            logger.debug("Saved template: " + template.name());
            return true;
        } catch (IOException e) {
            logger.error("Failed to save template " + template.name() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Serializes a template to YAML configuration.
     *
     * @param template Template to serialize
     * @param section Configuration section
     */
    private void serializeTemplate(ShopTemplate template, ConfigurationSection section) {
        section.set("name", template.name());
        section.set("owner", template.owner() != null ? template.owner().toString() : "SERVER");
        section.set("signType", template.signType().name());
        section.set("description", template.description());
        section.set("category", template.category());
        section.set("tags", template.tags());
        section.set("createdAt", template.createdAt().format(DATE_FORMATTER));
        section.set("isServerPreset", template.isServerPreset());
    }

    /**
     * Creates a template from an existing shop.
     *
     * @param name Template name
     * @param owner Template owner (null for server preset)
     * @param barterSign Shop to create template from
     * @param description Optional description
     * @param category Template category
     * @param tags Comma-separated tags
     * @param isServerPreset True if admin-defined preset
     * @return Created template
     */
    public ShopTemplate createFromShop(String name, UUID owner, BarterSign barterSign,
                                       String description, String category, String tags,
                                       boolean isServerPreset) {
        String id = UUID.randomUUID().toString();

        ShopTemplate template = ShopTemplate.builder()
            .id(id)
            .name(name)
            .owner(owner)
            .signType(barterSign.getType())
            .description(description)
            .category(category)
            .tags(tags)
            .createdAt(LocalDateTime.now())
            .isServerPreset(isServerPreset)
            .build();

        saveTemplate(template);
        logger.info("Created template: " + name + " (ID: " + id + ")");
        return template;
    }

    /**
     * Gets a template by ID.
     *
     * @param id Template ID
     * @return Template, or null if not found
     */
    public ShopTemplate getTemplate(String id) {
        return templates.get(id);
    }

    /**
     * Gets a template by name (case-insensitive).
     *
     * @param name Template name
     * @return Template, or null if not found
     */
    public ShopTemplate getTemplateByName(String name) {
        return templates.values().stream()
            .filter(t -> t.name().equalsIgnoreCase(name))
            .findFirst()
            .orElse(null);
    }

    /**
     * Gets all templates owned by a player.
     *
     * @param owner Player UUID
     * @return List of templates owned by player
     */
    public List<ShopTemplate> getTemplatesByOwner(UUID owner) {
        return templates.values().stream()
            .filter(t -> t.isOwnedBy(owner))
            .collect(Collectors.toList());
    }

    /**
     * Gets all server preset templates.
     *
     * @return List of server preset templates
     */
    public List<ShopTemplate> getServerPresets() {
        return templates.values().stream()
            .filter(ShopTemplate::isServerPreset)
            .collect(Collectors.toList());
    }

    /**
     * Gets all templates in a category.
     *
     * @param category Category name
     * @return List of templates in category
     */
    public List<ShopTemplate> getTemplatesByCategory(String category) {
        return templates.values().stream()
            .filter(t -> t.category().equalsIgnoreCase(category))
            .collect(Collectors.toList());
    }

    /**
     * Gets all templates matching tags.
     *
     * @param tags Comma-separated tags to search
     * @return List of matching templates
     */
    public List<ShopTemplate> getTemplatesByTags(String tags) {
        return templates.values().stream()
            .filter(t -> t.matchesTags(tags))
            .collect(Collectors.toList());
    }

    /**
     * Gets all templates.
     *
     * @return All templates
     */
    public Collection<ShopTemplate> getAllTemplates() {
        return new ArrayList<>(templates.values());
    }

    /**
     * Deletes a template.
     *
     * @param id Template ID to delete
     * @return true if deleted successfully
     */
    public boolean deleteTemplate(String id) {
        ShopTemplate template = templates.get(id);
        if (template == null) {
            return false;
        }

        try {
            ConfigurationSection templatesSection = templatesConfig.getConfigurationSection("templates");
            if (templatesSection != null) {
                templatesSection.set(id, null);
                templatesConfig.save(templatesFile);
            }

            templates.remove(id);
            logger.info("Deleted template: " + template.name());
            return true;
        } catch (IOException e) {
            logger.error("Failed to delete template " + id + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Reloads templates from disk.
     */
    public void reload() {
        logger.info("Reloading templates...");
        templatesConfig = YamlConfiguration.loadConfiguration(templatesFile);
        loadTemplates();
        logger.info("Reloaded " + templates.size() + " templates");
    }

    /**
     * Cleanup on plugin disable.
     */
    public void cleanup() {
        logger.debug("TemplateManager cleanup");
        templates.clear();
    }
}
