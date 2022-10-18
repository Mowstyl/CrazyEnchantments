package com.badbones69.crazyenchantments.api;

import com.badbones69.crazyenchantments.api.PluginSupport.SupportedPlugins;
import com.badbones69.crazyenchantments.CrazyEnchantments;
import com.badbones69.crazyenchantments.Starter;
import com.badbones69.crazyenchantments.api.economy.Currency;
import com.badbones69.crazyenchantments.Methods;
import com.badbones69.crazyenchantments.api.FileManager.Files;
import com.badbones69.crazyenchantments.api.enums.CEnchantments;
import com.badbones69.crazyenchantments.api.enums.Dust;
import com.badbones69.crazyenchantments.api.enums.Scrolls;
import com.badbones69.crazyenchantments.api.enums.ShopOption;
import com.badbones69.crazyenchantments.api.managers.*;
import com.badbones69.crazyenchantments.api.support.CropManager;
import com.badbones69.crazyenchantments.api.support.claims.WorldGuardSupport;
import com.badbones69.crazyenchantments.api.support.interfaces.CropManagerVersion;
import com.badbones69.crazyenchantments.api.support.interfaces.claims.WorldGuardVersion;
import com.badbones69.crazyenchantments.api.objects.*;
import com.badbones69.crazyenchantments.controllers.settings.ProtectionCrystalSettings;
import com.badbones69.crazyenchantments.listeners.ScramblerListener;
import com.badbones69.crazyenchantments.listeners.ScrollListener;
import com.badbones69.crazyenchantments.utilities.WingsUtils;
import com.google.common.collect.Lists;
import de.tr7zw.changeme.nbtapi.NBTItem;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.potion.PotionEffectType;
import java.util.*;
import java.util.Map.Entry;

public class CrazyManager {

    private final CrazyEnchantments plugin = CrazyEnchantments.getPlugin();

    private final Starter starter = plugin.getStarter();

    private final Methods methods = starter.getMethods();

    // Settings.
    private final ProtectionCrystalSettings protectionCrystalSettings = starter.getProtectionCrystalSettings();

    // Listeners.
    private final ScramblerListener scramblerListener = plugin.getScramblerListener();
    private final ScrollListener scrollListener = plugin.getScrollListener();

    private CropManagerVersion cropManagerVersion;
    private WorldGuardVersion worldGuardVersion;

    // Plugin Managers.
    private BlackSmithManager blackSmithManager;

    private InfoMenuManager infoMenuManager;

    private final AllyManager allyManager = starter.getAllyManager();

    // Wings.
    private final WingsManager wingsManager = starter.getWingsManager();
    private final WingsUtils wingsUtils = starter.getWingsUtils();

    private final ShopManager shopManager = starter.getShopManager();
    private final BowEnchantmentManager bowEnchantmentManager = starter.getBowEnchantmentManager();
    private final ArmorEnchantmentManager armorEnchantmentManager = starter.getArmorEnchantmentManager();

    // Arrays.
    private final List<Category> categories = Lists.newArrayList();
    private final List<GKitz> gkitz = Lists.newArrayList();
    private final List<CEPlayer> players = Lists.newArrayList();
    private final List<Material> blockList = Lists.newArrayList();
    private final List<CEnchantment> registeredEnchantments = Lists.newArrayList();
    private final List<Event> ignoredEvents = Lists.newArrayList();
    private final List<UUID> ignoredUUIDs = Lists.newArrayList();

    // Random
    private final Random random = new Random();

    private int rageMaxLevel;
    private boolean gkitzToggle;
    private boolean useUnsafeEnchantments;
    private boolean breakRageOnDamage;
    private boolean enchantStackedItems;
    private boolean maxEnchantmentCheck;
    private boolean checkVanillaLimit;

    private boolean dropBlocksBlast;
    private ItemBuilder enchantmentBook;
    private String whiteScrollProtectionName;

    /**
     * Loads everything for the Crazy Enchantments plugin.
     * Do not use unless needed.
     */
    public void load() {
        FileConfiguration config = Files.CONFIG.getFile();
        FileConfiguration gkit = Files.GKITZ.getFile();
        FileConfiguration enchants = Files.ENCHANTMENTS.getFile();

        FileConfiguration blocks = Files.BLOCKLIST.getFile();

        blockList.clear();
        gkitz.clear();
        registeredEnchantments.clear();
        categories.clear();

        // Check if we should patch player health.
        boolean playerHealthPatch = config.getBoolean("Settings.Reset-Players-Max-Health");

        plugin.getServer().getOnlinePlayers().forEach(player -> {
            // Load our players.
            loadCEPlayer(player);

            // Check if we need to patch playerHealth.
            Attribute genericAttribute = Attribute.GENERIC_MAX_HEALTH;

            double baseValue = player.getAttribute(genericAttribute).getBaseValue();

            if (playerHealthPatch) player.getAttribute(genericAttribute).setBaseValue(baseValue);

            // Loop through all players & back them up.
            plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, task -> getCEPlayers().forEach(this::backupCEPlayer), 5 * 20 * 60, 5 * 20 * 60);
        });

        // Invalidate cached enchants.
        CEnchantments.invalidateCachedEnchants();

        // Loop through block list.
        blocks.getStringList("Block-List").forEach(id -> {
            try {
                blockList.add(new ItemBuilder().setMaterial(id).getMaterial());
            } catch (Exception ignored) {}
        });

        // Loads the blacksmith manager.
        blackSmithManager = new BlackSmithManager();
        blackSmithManager.load();

        // Loads the info menu manager and the enchantment types.
        infoMenuManager = new InfoMenuManager();
        infoMenuManager.load();

        whiteScrollProtectionName = methods.color(config.getString("Settings.WhiteScroll.ProtectedName"));
        enchantmentBook = new ItemBuilder().setMaterial(config.getString("Settings.Enchantment-Book-Item"));
        useUnsafeEnchantments = config.getBoolean("Settings.EnchantmentOptions.UnSafe-Enchantments");
        maxEnchantmentCheck = config.getBoolean("Settings.EnchantmentOptions.MaxAmountOfEnchantmentsToggle");
        checkVanillaLimit = config.getBoolean("Settings.EnchantmentOptions.IncludeVanillaEnchantments");
        gkitzToggle = !config.contains("Settings.GKitz.Enabled") || config.getBoolean("Settings.GKitz.Enabled");
        rageMaxLevel = config.contains("Settings.EnchantmentOptions.MaxRageLevel") ? config.getInt("Settings.EnchantmentOptions.MaxRageLevel") : 4;
        breakRageOnDamage = !config.contains("Settings.EnchantmentOptions.Break-Rage-On-Damage") || config.getBoolean("Settings.EnchantmentOptions.Break-Rage-On-Damage");
        enchantStackedItems = config.contains("Settings.EnchantmentOptions.Enchant-Stacked-Items") && config.getBoolean("Settings.EnchantmentOptions.Enchant-Stacked-Items");
        setDropBlocksBlast(config.getBoolean("Settings.EnchantmentOptions.Drop-Blocks-For-Blast", true));

        for (String category : config.getConfigurationSection("Categories").getKeys(false)) {
            String path = "Categories." + category;
            LostBook lostBook = new LostBook(
                    config.getInt(path + ".LostBook.Slot"),
                    config.getBoolean(path + ".LostBook.InGUI"),
                    new ItemBuilder()
                    .setMaterial(config.getString(path + ".LostBook.Item"))
                    .setPlayerName(config.getString(path + ".LostBook.Player"))
                    .setName(config.getString(path + ".LostBook.Name"))
                    .setLore(config.getStringList(path + ".LostBook.Lore"))
                    .setGlow(config.getBoolean(path + ".LostBook.Glowing")),
                    config.getInt(path + ".LostBook.Cost"),
                    Currency.getCurrency(config.getString(path + ".LostBook.Currency")),
                    config.getBoolean(path + ".LostBook.FireworkToggle"),
                    getColors(config.getString(path + ".LostBook.FireworkColors")),
                    config.getBoolean(path + ".LostBook.Sound-Toggle"),
                    config.getString(path + ".LostBook.Sound"));
            categories.add(new Category(
                    category,
                    config.getInt(path + ".Slot"),
                    config.getBoolean(path + ".InGUI"),
                    new ItemBuilder()
                    .setMaterial(config.getString(path + ".Item"))
                    .setPlayerName(config.getString(path + ".Player"))
                    .setName(config.getString(path + ".Name"))
                    .setLore(config.getStringList(path + ".Lore"))
                    .setGlow(config.getBoolean(path + ".Glowing")),
                    config.getInt(path + ".Cost"),
                    Currency.getCurrency(config.getString(path + ".Currency")),
                    config.getInt(path + ".Rarity"),
                    lostBook,
                    config.getInt(path + ".EnchOptions.SuccessPercent.Max"),
                    config.getInt(path + ".EnchOptions.SuccessPercent.Min"),
                    config.getInt(path + ".EnchOptions.DestroyPercent.Max"),
                    config.getInt(path + ".EnchOptions.DestroyPercent.Min"),
                    config.getBoolean(path + ".EnchOptions.MaxLvlToggle"),
                    config.getInt(path + ".EnchOptions.LvlRange.Max"),
                    config.getInt(path + ".EnchOptions.LvlRange.Min")));
        }

        for (CEnchantments cEnchantment : CEnchantments.values()) {
            String name = cEnchantment.getName();
            String path = "Enchantments." + name;

            if (enchants.contains(path)) { // To make sure the enchantment isn't broken.
                CEnchantment enchantment = new CEnchantment(name)
                .setCustomName(enchants.getString(path + ".Name"))
                .setActivated(enchants.getBoolean(path + ".Enabled"))
                .setColor(enchants.getString(path + ".Color"))
                .setBookColor(enchants.getString(path + ".BookColor"))
                .setMaxLevel(enchants.getInt(path + ".MaxPower"))
                .setEnchantmentType(cEnchantment.getType())
                .setInfoName(enchants.getString(path + ".Info.Name"))
                .setInfoDescription(enchants.getStringList(path + ".Info.Description"))
                .setCategories(enchants.getStringList(path + ".Categories"))
                .setChance(cEnchantment.getChance())
                .setChanceIncrease(cEnchantment.getChanceIncrease());

                if (enchants.contains(path + ".Enchantment-Type")) enchantment.setEnchantmentType(methods.getFromName(enchants.getString(path + ".Enchantment-Type")));

                if (cEnchantment.hasChanceSystem()) {
                    if (enchants.contains(path + ".Chance-System.Base")) {
                        enchantment.setChance(enchants.getInt(path + ".Chance-System.Base"));
                    } else {
                        enchantment.setChance(cEnchantment.getChance());
                    }

                    if (enchants.contains(path + ".Chance-System.Increase")) {
                        enchantment.setChanceIncrease(enchants.getInt(path + ".Chance-System.Increase"));
                    } else {
                        enchantment.setChanceIncrease(cEnchantment.getChanceIncrease());
                    }
                }

                enchantment.registerEnchantment();
            }
        }

        if (gkitzToggle) {
            for (String kit : gkit.getConfigurationSection("GKitz").getKeys(false)) {
                String path = "GKitz." + kit + ".";
                int slot = gkit.getInt(path + "Display.Slot");
                String time = gkit.getString(path + "Cooldown");
                boolean autoEquip = gkit.getBoolean(path + "Auto-Equip");
                NBTItem displayItem = new NBTItem(new ItemBuilder()
                .setMaterial(gkit.getString(path + "Display.Item"))
                .setName(gkit.getString(path + "Display.Name"))
                .setLore(gkit.getStringList(path + "Display.Lore"))
                .setGlow(gkit.getBoolean(path + "Display.Glowing")).build());
                displayItem.setString("gkit", kit);
                List<String> commands = gkit.getStringList(path + "Commands");
                List<String> itemStrings = gkit.getStringList(path + "Items");
                List<ItemStack> previewItems = getInfoGKit(itemStrings);
                previewItems.addAll(getInfoGKit(gkit.getStringList(path + "Fake-Items")));
                gkitz.add(new GKitz(kit, slot, time, displayItem.getItem(), previewItems, commands, itemStrings, autoEquip));
            }
        }

        // Load all scroll types.
        Scrolls.loadScrolls();
        // Load all dust types.
        Dust.loadDust();

        // Loads the protection crystals.
        protectionCrystalSettings.loadProtectionCrystal();
        // Loads the scrambler.
        scramblerListener.loadScrambler();
        // Loads the Scroll Control settings.
        scrollListener.loadScrollControl();

        cropManagerVersion = new CropManager();

        // Loads the scrolls.
        Scrolls.loadScrolls();
        // Loads the dust.
        Dust.loadDust();

        // Loads the ShopOptions.
        ShopOption.loadShopOptions();

        // Loads the shop manager.
        shopManager.load();

        // Loads the settings for wings enchantment.
        wingsManager.load();

        // Loads the settings for the bow enchantments.
        bowEnchantmentManager.load();

        // Loads the settings for the armor enchantments.
        armorEnchantmentManager.load();

        // Loads the settings for the ally enchantments.
        allyManager.load();

        // Starts the wings task.
        wingsUtils.startWings();

        if (SupportedPlugins.WORLDGUARD.isPluginLoaded() && SupportedPlugins.WORLDEDIT.isPluginLoaded()) worldGuardVersion = new WorldGuardSupport();
    }

    /**
     * Only needs used when the player joins the server.
     * This plugin does it automatically, so there is no need to use it unless you have to.
     * @param player The player you wish to load.
     */
    public void loadCEPlayer(Player player) {
        FileConfiguration data = Files.DATA.getFile();
        String uuid = player.getUniqueId().toString();
        int souls = 0;
        boolean isActive = false;

        if (data.contains("Players." + uuid + ".Souls-Information")) {
            souls = data.getInt("Players." + uuid + ".Souls-Information.Souls");
            isActive = data.getBoolean("Players." + uuid + ".Souls-Information.Is-Active");
        }

        List<Cooldown> cooldowns = new ArrayList<>();

        for (GKitz kit : getGKitz()) {
            if (data.contains("Players." + uuid + ".GKitz." + kit.getName())) {
                Calendar cooldown = Calendar.getInstance();
                cooldown.setTimeInMillis(data.getLong("Players." + uuid + ".GKitz." + kit.getName()));
                cooldowns.add(new Cooldown(kit, cooldown));
            }
        }

        addCEPlayer(new CEPlayer(player, souls, isActive, cooldowns));
    }

    /**
     * Only needs used when the player leaves the server.
     * This plugin removes the player automatically, so don't use this method unless needed for some reason.
     * @param player Player you wish to remove.
     */
    public void unloadCEPlayer(Player player) {
        FileConfiguration data = Files.DATA.getFile();
        String uuid = player.getUniqueId().toString();
        CEPlayer cePlayer = getCEPlayer(player);

        if (cePlayer != null) {

            if (cePlayer.getSouls() > 0) {
                data.set("Players." + uuid + ".Name", player.getName());
                data.set("Players." + uuid + ".Souls-Information.Souls", cePlayer.getSouls());
                data.set("Players." + uuid + ".Souls-Information.Is-Active", cePlayer.isSoulsActive());
            }

            for (Cooldown cooldown : cePlayer.getCooldowns()) {
                data.set("Players." + uuid + ".GKitz." + cooldown.getGKitz().getName(), cooldown.getCooldown().getTimeInMillis());
            }

            Files.DATA.saveFile();
        }

        removeCEPlayer(cePlayer);
    }

    /**
     * This backup all the players data stored by this plugin.
     * @param player The player you wish to back up.
     */
    public void backupCEPlayer(Player player) {
        backupCEPlayer(getCEPlayer(player));
    }

    /**
     * This backup all the players data stored by this plugin.
     * @param cePlayer The player you wish to back up.
     */
    public void backupCEPlayer(CEPlayer cePlayer) {
        FileConfiguration data = Files.DATA.getFile();
        String uuid = cePlayer.getPlayer().getUniqueId().toString();

        if (cePlayer.getSouls() > 0) {
            data.set("Players." + uuid + ".Name", cePlayer.getPlayer().getName());
            data.set("Players." + uuid + ".Souls-Information.Souls", cePlayer.getSouls());
            data.set("Players." + uuid + ".Souls-Information.Is-Active", cePlayer.isSoulsActive());
        }

        for (Cooldown cooldown : cePlayer.getCooldowns()) {
            data.set("Players." + uuid + ".GKitz." + cooldown.getGKitz().getName(), cooldown.getCooldown().getTimeInMillis());
        }

        Files.DATA.saveFile();
    }

    /**
     * @return World Guard support class.
     */
    public WorldGuardVersion getWorldGuardSupport() {
        return worldGuardVersion;
    }

    /**
     * @return NMS support class.
     */
    public CropManagerVersion getNMSSupport() {
        return cropManagerVersion;
    }

    /**
     * Check if the config has unsafe enchantments enabled.
     * @return True if enabled and false if not.
     */
    public boolean useUnsafeEnchantments() {
        return useUnsafeEnchantments;
    }

    public boolean useMaxEnchantmentLimit() {
        return maxEnchantmentCheck;
    }

    public boolean checkVanillaLimit() {
        return checkVanillaLimit;
    }

    /**
     * Check if the gkitz option is enabled.
     * @return True if it is on and false if it is off.
     */
    public boolean isGkitzEnabled() {
        return gkitzToggle;
    }

    /**
     * Get a GKit from its name.
     * @param kitName The kit you wish to get.
     * @return The kit as a GKitz object.
     */
    public GKitz getGKitFromName(String kitName) {
        for (GKitz kit : getGKitz()) {
            if (kit.getName().equalsIgnoreCase(kitName)) return kit;
        }

        return null;
    }

    /**
     * Get all loaded gkitz.
     * @return All the loaded gkitz.
     */
    public List<GKitz> getGKitz() {
        return gkitz;
    }

    /**
     * Add a new GKit to the plugin.
     * @param kit The kit you wish to add.
     */
    public void addGKit(GKitz kit) {
        gkitz.add(kit);
    }

    /**
     * Remove a kit that is in the plugin.
     * @param kit The kit you wish to remove.
     */
    public void removeGKit(GKitz kit) {
        gkitz.remove(kit);
    }

    /**
     * This converts a normal Player into a CEPlayer that is loaded.
     * @param player The player you want to get as a CEPlayer.
     * @return The player but as a CEPlayer. Will return null if not found.
     */
    public CEPlayer getCEPlayer(Player player) {
        for (CEPlayer cePlayer : getCEPlayers()) {
            if (cePlayer.getPlayer() == player) return cePlayer;
        }

        return null;
    }

    /**
     * This gets all the CEPlayer's that are loaded.
     * @return All CEPlayer's that are loading and in a list.
     */
    public List<CEPlayer> getCEPlayers() {
        return players;
    }

    /**
     * @return a clone of the ItemBuilder of the enchantment book.
     */
    public ItemBuilder getEnchantmentBook() {
        return enchantmentBook.copy();
    }

    /**
     * @return the itemstack of the enchantment book.
     */
    public ItemStack getEnchantmentBookItem() {
        return enchantmentBook.build();
    }

    /**
     * @param item Item you want to check to see if it has enchantments.
     * @return True if it has enchantments / False if it doesn't have enchantments.
     */
    public boolean hasEnchantments(ItemStack item) {
        for (CEnchantment enchantment : registeredEnchantments) {
            if (hasEnchantment(item, enchantment)) return true;
        }

        return false;
    }

    /**
     * @param item Item that you want to check if it has an enchantment.
     * @param enchantment The enchantment you want to check if the item has.
     * @return True if the item has the enchantment / False if it doesn't have the enchantment.
     */
    public boolean hasEnchantment(ItemStack item, CEnchantment enchantment) {
        if (methods.verifyItemLore(item)) {
            ItemMeta meta = item.getItemMeta();
            List<String> itemLore = meta.getLore();

            if (enchantment.isActivated() && itemLore != null) {
                for (String lore : itemLore) {
                    String[] split = lore.split(" ");
                    // Split can generate an empty array in rare case.
                    if (split.length > 0 && lore.replace(" " + split[split.length - 1], "").equals(enchantment.getColor() + enchantment.getCustomName())) return true;
                }
            }
        }

        return false;
    }

    /**
     * @param item Item that you want to check if it has an enchantment.
     * @param enchantment The enchantment you want to check if the item has.
     * @return True if the item has the enchantment / False if it doesn't have the enchantment.
     */
    public boolean hasEnchantment(ItemStack item, CEnchantments enchantment) {
        return hasEnchantment(item, enchantment.getEnchantment());
    }

    /**
     * Get the highest category rarity the enchantment is in.
     * @param enchantment The enchantment you are checking.
     * @return The highest category based on the rarities.
     */
    public Category getHighestEnchantmentCategory(CEnchantment enchantment) {
        Category topCategory = null;
        int rarity = 0;

        for (Category category : enchantment.getCategories()) {
            if (category.getRarity() >= rarity) {
                rarity = category.getRarity();
                topCategory = category;
            }
        }

        return topCategory;
    }

    /**
     * Get all the categories that can be used.
     * @return List of all the categories.
     */
    public List<Category> getCategories() {
        return categories;
    }

    /**
     * @param name The name of the category you want.
     * @return The category object.
     */
    public Category getCategory(String name) {
        for (Category category : categories) {
            if (category.getName().equalsIgnoreCase(name)) return category;
        }

        return null;
    }

    /**
     * Get the category of a lostbook from an itemstack.
     * @param item The itemstack you are checking.
     * @return The category it has or null if not found.
     */
    public Category getCategoryFromLostBook(ItemStack item) {
        for (Category category : categories) {
            if (item.isSimilar(category.getLostBook().getLostBook(category).build())) return category;
        }

        return null;
    }

    public CEBook getRandomEnchantmentBook(Category category) {
        try {
            List<CEnchantment> enchantments = category.getEnabledEnchantments();
            CEnchantment enchantment = enchantments.get(random.nextInt(enchantments.size()));

            return new CEBook(enchantment, randomLevel(enchantment, category), 1, category);
        } catch (Exception e) {
            plugin.getLogger().info("The category " + category.getName() + " has no enchantments."
            + " Please add enchantments to the category in the Enchantments.yml. If you do not wish to have the category feel free to delete it from the Config.yml.");
            return null;
        }
    }

    /**
     * @param player The player you want to check if they have the enchantment on their armor.
     * @param includeItem The item you want to include.
     * @param excludeItem The item you want to exclude.
     * @param enchantment The enchantment you are checking.
     * @return True if a piece of armor has the enchantment and false if not.
     */
    public boolean playerHasEnchantmentOn(Player player, ItemStack includeItem, ItemStack excludeItem, CEnchantment enchantment) {
        for (ItemStack armor : player.getEquipment().getArmorContents()) {
            if (!armor.isSimilar(excludeItem) && hasEnchantment(armor, enchantment)) return true;
        }

        return hasEnchantment(includeItem, enchantment);
    }

    /**
     * @param player The player you want to check if they have the enchantment on their armor.
     * @param excludedItem The item you want to exclude.
     * @param enchantment The enchantment you are checking.
     * @return True if a piece of armor has the enchantment and false if not.
     */
    public boolean playerHasEnchantmentOnExclude(Player player, ItemStack excludedItem, CEnchantment enchantment) {
        for (ItemStack armor : player.getEquipment().getArmorContents()) {
            if (!armor.isSimilar(excludedItem) && hasEnchantment(armor, enchantment)) return true;
        }

        return false;
    }

    /**
     * @param player The player you want to check if they have the enchantment on their armor.
     * @param includedItem The item you want to include.
     * @param enchantment The enchantment you are checking.
     * @return True if a piece of armor has the enchantment and false if not.
     */
    public boolean playerHasEnchantmentOnInclude(Player player, ItemStack includedItem, CEnchantment enchantment) {
        for (ItemStack armor : player.getEquipment().getArmorContents()) {
            if (hasEnchantment(armor, enchantment)) return true;
        }

        return hasEnchantment(includedItem, enchantment);
    }

    /**
     * @param player The player you want to get the highest level of an enchantment from.
     * @param includedItem The item you want to include.
     * @param excludedItem The item you want to exclude.
     * @param enchantment The enchantment you are checking.
     * @return The highest level of the enchantment that the player currently has.
     */
    public int getHighestEnchantmentLevel(Player player, ItemStack includedItem, ItemStack excludedItem, CEnchantment enchantment) {
        int highest;

        highest = checkHighEnchant(player, excludedItem, enchantment);

        if (hasEnchantment(includedItem, enchantment)) {
            int level = getLevel(includedItem, enchantment);

            if (highest < level) highest = level;
        }

        return highest;
    }

    /**
     * @param player The player you want to get the highest level of an enchantment from.
     * @param excludedItem The item you want to exclude.
     * @param enchantment The enchantment you are checking.
     * @return The highest level of the enchantment that the player currently has.
     */
    public int getHighestEnchantmentLevelExclude(Player player, ItemStack excludedItem, CEnchantment enchantment) {
        return checkHighEnchant(player, excludedItem, enchantment);
    }

    private int checkHighEnchant(Player player, ItemStack excludedItem, CEnchantment enchantment) {
        int highest = 0;

        for (ItemStack armor : player.getEquipment().getArmorContents()) {
            if (!armor.isSimilar(excludedItem) && hasEnchantment(armor, enchantment)) {
                int level = getLevel(armor, enchantment);

                if (highest < level) highest = level;
            }
        }

        return highest;
    }

    /**
     * @param player The player you want to get the highest level of an enchantment from.
     * @param includedItem The item you want to include.
     * @param enchantment The enchantment you are checking.
     * @return The highest level of the enchantment that the player currently has.
     */
    public int getHighestEnchantmentLevelInclude(Player player, ItemStack includedItem, CEnchantment enchantment) {
        int highest = 0;

        for (ItemStack armor : player.getEquipment().getArmorContents()) {
            if (hasEnchantment(armor, enchantment)) {
                int level = getLevel(armor, enchantment);

                if (highest < level) highest = level;
            }
        }

        if (hasEnchantment(includedItem, enchantment)) {
            int level = getLevel(includedItem, enchantment);
            if (highest < level) highest = level;
        }

        return highest;
    }

    /**
     * Get all the current registered enchantments.
     * @return A list of all the registered enchantments in the plugin.
     */
    public List<CEnchantment> getRegisteredEnchantments() {
        return new ArrayList<>(registeredEnchantments);
    }

    /**
     * Get a CEnchantment enchantment from the name.
     * @param enchantmentString The name of the enchantment.
     * @return The enchantment as a CEnchantment but if not found will be null.
     */
    public CEnchantment getEnchantmentFromName(String enchantmentString) {
        enchantmentString = methods.stripString(enchantmentString);

        for (CEnchantment enchantment : registeredEnchantments) {
            if (methods.stripString(enchantment.getName()).equalsIgnoreCase(enchantmentString) ||
            methods.stripString(enchantment.getCustomName()).equalsIgnoreCase(enchantmentString)) return enchantment;
        }

        return null;
    }

    /**
     * Register a new enchantment into the plugin.
     * @param enchantment The enchantment you wish to register.
     */
    public void registerEnchantment(CEnchantment enchantment) {
        registeredEnchantments.add(enchantment);
    }

    /**
     * Unregister an enchantment that is registered into plugin.
     * @param enchantment The enchantment you wish to unregister.
     */
    public void unregisterEnchantment(CEnchantment enchantment) {
        registeredEnchantments.remove(enchantment);
    }

    /**
     * @param item Item you want to add the enchantment to.
     * @param enchantment Enchantment you want added.
     * @param level Tier of the enchantment.
     * @return The item with the enchantment on it.
     */
    public ItemStack addEnchantment(ItemStack item, CEnchantment enchantment, int level) {
        Map<CEnchantment, Integer> enchantments = new HashMap<>();

        enchantments.put(enchantment, level);
        return addEnchantments(item, enchantments);
    }

    public ItemStack addEnchantments(ItemStack item, Map<CEnchantment, Integer> enchantments) {
        for (Entry<CEnchantment, Integer> entry : enchantments.entrySet()) {
            CEnchantment enchantment = entry.getKey();
            int level = entry.getValue();

            if (hasEnchantment(item, enchantment)) removeEnchantment(item, enchantment);

            List<String> newLore = new ArrayList<>();
            List<String> lores = new ArrayList<>();
            HashMap<String, String> enchantmentStrings = new HashMap<>();

            for (CEnchantment en : getEnchantmentsOnItem(item)) {
                enchantmentStrings.put(en.getName(), methods.color(en.getColor() + en.getCustomName() + " " + convertLevelString(getLevel(item, en))));
                removeEnchantment(item, en);
            }

            ItemMeta meta = item.getItemMeta();

            if (meta != null && meta.hasLore()) {
                List<String> itemLore = meta.getLore();

                if (itemLore != null) lores.addAll(itemLore);
            }

            enchantmentStrings.put(enchantment.getName(), methods.color(enchantment.getColor() + enchantment.getCustomName() + " " + convertLevelString(level)));

            for (Entry<String, String> stringEntry : enchantmentStrings.entrySet()) {
                newLore.add(stringEntry.getValue());
            }

            newLore.addAll(lores);

            if (meta != null) meta.setLore(newLore);

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * @param item Item you want to remove the enchantment from.
     * @param enchant Enchantment you want removed.
     * @return Item without the enchantment.
     */
    public ItemStack removeEnchantment(ItemStack item, CEnchantment enchant) {
        List<String> newLore = new ArrayList<>();
        ItemMeta meta = item.getItemMeta();

        if (meta != null && meta.hasLore()) {
            List<String> itemLore = meta.getLore();

            if (itemLore != null) {
                for (String lore : itemLore) {
                    if (!lore.contains(enchant.getCustomName())) newLore.add(lore);
                }
            }
        }

        if (meta != null) meta.setLore(newLore);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Note: If the enchantment is not active it will not be added to the list.
     * @param item Item you want to get the enchantments from.
     * @return A list of enchantments the item has.
     */
    public List<CEnchantment> getEnchantmentsOnItem(ItemStack item) {
        return new ArrayList<>(getEnchantments(item).keySet());
    }

    /**
     * Note: If the enchantment is not active it will not be added to the Map.
     * @param item Item you want to get the enchantments from.
     * @return A Map of all enchantments and their levels on the item.
     */
    public Map<CEnchantment, Integer> getEnchantments(ItemStack item) {

        if (!methods.verifyItemLore(item)) return Collections.emptyMap();

        List<String> lore = item.getItemMeta().getLore();
        Map<CEnchantment, Integer> enchantments = null;

        assert lore != null;
        for (String line : lore) {
            int lastSpaceIndex = line.lastIndexOf(' ');

            if (lastSpaceIndex < 1 || lastSpaceIndex + 1 > line.length()) continue;

            String enchantmentName = line.substring(0, lastSpaceIndex);

            for (CEnchantment enchantment : registeredEnchantments) {
                if (!enchantment.isActivated()) continue;

                if (!enchantmentName.equals(enchantment.getColor() + enchantment.getCustomName())) continue;

                String levelString = line.substring(lastSpaceIndex + 1);
                int level = convertLevelInteger(levelString);

                if (level < 1) break;

                if (enchantments == null) enchantments = new HashMap<>();

                enchantments.put(enchantment, level);
                break; // Next line
            }
        }

        if (enchantments == null) enchantments = Collections.emptyMap();

        return enchantments;
    }

    public int getEnchantmentAmount(ItemStack item) {
        int amount = getEnchantmentsOnItem(item).size();

        if (checkVanillaLimit) {
            if (item.hasItemMeta()) {
                if (item.getItemMeta().hasEnchants()) amount += item.getItemMeta().getEnchants().size();
            }
        }

        return amount;
    }

    public boolean hasWhiteScrollProtection(ItemStack item) {
        ItemMeta meta = item.getItemMeta();

        if (meta != null && meta.hasLore()) {
            List<String> itemLore = meta.getLore();

            if (itemLore != null) {
                for (String lore : itemLore) {
                    if (lore.equals(whiteScrollProtectionName)) return true;
                }
            }
        }

        return false;
    }

    public ItemStack addWhiteScrollProtection(ItemStack item) {
        return ItemBuilder.convertItemStack(item).addLore(whiteScrollProtectionName).build();
    }

    public ItemStack removeWhiteScrollProtection(ItemStack item) {
        ItemMeta itemMeta = item.getItemMeta();

        if (itemMeta != null && itemMeta.hasLore()) {
            List<String> newLore = new ArrayList<>(Objects.requireNonNull(itemMeta.getLore()));
            newLore.remove(whiteScrollProtectionName);
            itemMeta.setLore(newLore);
            item.setItemMeta(itemMeta);
        }

        return item;
    }

    /**
     * Force an update of a players armor potion effects.
     * @param player The player you are updating the effects of.
     */
    public void updatePlayerEffects(Player player) {
        if (player != null) {
            for (CEnchantments ench : getEnchantmentPotions().keySet()) {
                for (ItemStack armor : player.getEquipment().getArmorContents()) {
                    if (ench.isActivated() && hasEnchantment(armor, ench.getEnchantment())) {
                        Map<PotionEffectType, Integer> effects = getUpdatedEffects(player, armor, new ItemStack(Material.AIR), ench);
                        methods.loopEffectsMap(effects, player);
                    }
                }
            }
        }
    }

    /**
     * @param player The player you are adding it to.
     * @param includedItem Include an item.
     * @param excludedItem Exclude an item.
     * @param enchantment The enchantment you want the max level effects from.
     * @return The list of all the max potion effects based on all the armor on the player.
     */
    public Map<PotionEffectType, Integer> getUpdatedEffects(Player player, ItemStack includedItem, ItemStack excludedItem, CEnchantments enchantment) {
        HashMap<PotionEffectType, Integer> effects = new HashMap<>();
        List<ItemStack> items = new ArrayList<>(Arrays.asList(player.getEquipment().getArmorContents()));

        if (includedItem == null) includedItem = new ItemStack(Material.AIR);

        if (excludedItem == null) excludedItem = new ItemStack(Material.AIR);

        if (excludedItem.isSimilar(includedItem)) excludedItem = new ItemStack(Material.AIR);

        items.add(includedItem);
        Map<CEnchantments, HashMap<PotionEffectType, Integer>> armorEffects = getEnchantmentPotions();

        for (Entry<CEnchantments, HashMap<PotionEffectType, Integer>> enchantments : armorEffects.entrySet()) {
            if (enchantments.getKey().isActivated()) {
                for (ItemStack armor : items) {
                    if (armor != null && !armor.isSimilar(excludedItem) && hasEnchantment(armor, enchantments.getKey().getEnchantment())) {
                        int level = getLevel(armor, enchantments.getKey().getEnchantment());

                        if (!useUnsafeEnchantments && level > enchantments.getKey().getEnchantment().getMaxLevel()) level = enchantments.getKey().getEnchantment().getMaxLevel();

                        for (PotionEffectType type : enchantments.getValue().keySet()) {
                            if (enchantments.getValue().containsKey(type)) {
                                if (effects.containsKey(type)) {
                                    int updated = effects.get(type);

                                    if (updated < (level + enchantments.getValue().get(type))) effects.put(type, level + enchantments.getValue().get(type));
                                } else {
                                    effects.put(type, level + enchantments.getValue().get(type));
                                }
                            }
                        }
                    }
                }
            }
        }

        for (PotionEffectType type : armorEffects.get(enchantment).keySet()) {
            if (!effects.containsKey(type)) effects.put(type, -1);
        }

        return effects;
    }

    /**
     * @return All the effects for each enchantment that needs it.
     */
    public Map<CEnchantments, HashMap<PotionEffectType, Integer>> getEnchantmentPotions() {
        HashMap<CEnchantments, HashMap<PotionEffectType, Integer>> enchants = new HashMap<>();

        enchants.put(CEnchantments.GLOWING, new HashMap<>());
        enchants.get(CEnchantments.GLOWING).put(PotionEffectType.NIGHT_VISION, -1);

        enchants.put(CEnchantments.MERMAID, new HashMap<>());
        enchants.get(CEnchantments.MERMAID).put(PotionEffectType.WATER_BREATHING, -1);

        enchants.put(CEnchantments.BURNSHIELD, new HashMap<>());
        enchants.get(CEnchantments.BURNSHIELD).put(PotionEffectType.FIRE_RESISTANCE, -1);

        enchants.put(CEnchantments.DRUNK, new HashMap<>());
        enchants.get(CEnchantments.DRUNK).put(PotionEffectType.INCREASE_DAMAGE, -1);
        enchants.get(CEnchantments.DRUNK).put(PotionEffectType.SLOW_DIGGING, -1);
        enchants.get(CEnchantments.DRUNK).put(PotionEffectType.SLOW, 0);

        enchants.put(CEnchantments.HULK, new HashMap<>());
        enchants.get(CEnchantments.HULK).put(PotionEffectType.INCREASE_DAMAGE, -1);
        enchants.get(CEnchantments.HULK).put(PotionEffectType.DAMAGE_RESISTANCE, -1);
        enchants.get(CEnchantments.HULK).put(PotionEffectType.SLOW, 0);

        enchants.put(CEnchantments.VALOR, new HashMap<>());
        enchants.get(CEnchantments.VALOR).put(PotionEffectType.DAMAGE_RESISTANCE, -1);

        enchants.put(CEnchantments.OVERLOAD, new HashMap<>());
        enchants.get(CEnchantments.OVERLOAD).put(PotionEffectType.HEALTH_BOOST, 0);

        enchants.put(CEnchantments.NINJA, new HashMap<>());
        enchants.get(CEnchantments.NINJA).put(PotionEffectType.HEALTH_BOOST, -1);
        enchants.get(CEnchantments.NINJA).put(PotionEffectType.SPEED, -1);

        enchants.put(CEnchantments.INSOMNIA, new HashMap<>());
        enchants.get(CEnchantments.INSOMNIA).put(PotionEffectType.CONFUSION, -1);
        enchants.get(CEnchantments.INSOMNIA).put(PotionEffectType.SLOW_DIGGING, -1);
        enchants.get(CEnchantments.INSOMNIA).put(PotionEffectType.SLOW, 0);

        enchants.put(CEnchantments.ANTIGRAVITY, new HashMap<>());
        enchants.get(CEnchantments.ANTIGRAVITY).put(PotionEffectType.JUMP, 1);

        enchants.put(CEnchantments.GEARS, new HashMap<>());
        enchants.get(CEnchantments.GEARS).put(PotionEffectType.SPEED, -1);

        enchants.put(CEnchantments.SPRINGS, new HashMap<>());
        enchants.get(CEnchantments.SPRINGS).put(PotionEffectType.JUMP, -1);

        enchants.put(CEnchantments.CYBORG, new HashMap<>());
        enchants.get(CEnchantments.CYBORG).put(PotionEffectType.SPEED, -1);
        enchants.get(CEnchantments.CYBORG).put(PotionEffectType.INCREASE_DAMAGE, 0);
        enchants.get(CEnchantments.CYBORG).put(PotionEffectType.JUMP, 0);

        return enchants;
    }

    /**
     * This method converts an ItemStack into a CEBook.
     * @param book The ItemStack you are converting.
     * @return If the book is a CEBook it will return the CEBook object and if not it will return null.
     */
    public CEBook getCEBook(ItemStack book) {
        try {
            return new CEBook(getEnchantmentBookEnchantment(book), getBookLevel(book, getEnchantmentBookEnchantment(book)), book.getAmount())
            .setSuccessRate(methods.getPercent("%success_rate%", book, Files.CONFIG.getFile().getStringList("Settings.EnchantmentBookLore"), 100))
            .setDestroyRate(methods.getPercent("%destroy_rate%", book, Files.CONFIG.getFile().getStringList("Settings.EnchantmentBookLore"), 0));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Check if an itemstack is an enchantment book.
     * @param book The item you are checking.
     * @return True if it is and false if not.
     */
    public boolean isEnchantmentBook(ItemStack book) {
        if (book != null && book.getType() == enchantmentBook.getMaterial() && book.hasItemMeta() && book.getItemMeta().hasDisplayName()) {
            for (CEnchantment enchantment : registeredEnchantments) {
                String bookNameCheck = book.getItemMeta().getDisplayName();
                String[] split = bookNameCheck.split(" ");

                if (bookNameCheck.replace(" " + split[split.length - 1], "").equals(enchantment.getBookColor() + enchantment.getCustomName())) return true;
            }
        }

        return false;
    }

    /**
     * Get the enchantment from an enchantment book.
     * @param book The book you want the enchantment from.
     * @return The enchantment the book is.
     */
    public CEnchantment getEnchantmentBookEnchantment(ItemStack book) {
        if (book != null && book.getType() == enchantmentBook.getMaterial() && book.hasItemMeta() && book.getItemMeta().hasDisplayName()) {
            for (CEnchantment enchantment : registeredEnchantments) {
                String bookNameCheck = book.getItemMeta().getDisplayName();
                String[] split = bookNameCheck.split(" ");

                if (bookNameCheck.replace(" " + split[split.length - 1], "").equals(enchantment.getBookColor() + enchantment.getCustomName())) return enchantment;
            }
        }

        return null;
    }

    /**
     * Get a players max amount of enchantments.
     * @param player The player you are checking.
     * @return The max amount of enchantments a player can have on an item.
     */
    public int getPlayerMaxEnchantments(Player player) {
        int limit = 0;

        for (PermissionAttachmentInfo Permission : player.getEffectivePermissions()) {
            String perm = Permission.getPermission().toLowerCase();

            if (perm.startsWith("crazyenchantments.limit.")) {
                perm = perm.replace("crazyenchantments.limit.", "");

                if (methods.isInt(perm) && limit < Integer.parseInt(perm)) limit = Integer.parseInt(perm);
            }
        }

        return limit;
    }

    public boolean canAddEnchantment(Player player, ItemStack item) {
        if (maxEnchantmentCheck && !player.hasPermission("crazyenchantments.bypass.limit")) return getEnchantmentAmount(item) < getPlayerMaxEnchantments(player);

        return true;
    }

    /**
     * @param book The book you are getting the level from.
     * @param enchant The enchantment you want the level from.
     * @return The level the enchantment has.
     */
    public int getBookLevel(ItemStack book, CEnchantment enchant) {
        return convertLevelInteger(book.getItemMeta().getDisplayName().replace(enchant.getBookColor() + enchant.getCustomName() + " ", ""));
    }

    /**
     * @param item Item you are getting the level from.
     * @param enchant The enchantment you want the level from.
     * @return The level the enchantment has.
     */
    public int getLevel(ItemStack item, CEnchantment enchant) {
        int level = convertLevelInteger(checkLevels(item, enchant.getCustomName()).replace(enchant.getColor() + enchant.getCustomName() + " ", ""));

        if (!useUnsafeEnchantments && level > enchant.getMaxLevel()) level = enchant.getMaxLevel();

        return level;
    }

    /**
     * @param item Item you are getting the level from.
     * @param enchant The enchantment you want the level from.
     * @return The level the enchantment has.
     */
    public int getLevel(ItemStack item, CEnchantments enchant) {
        int level;

        level = convertLevelInteger(checkLevels(item, enchant.getCustomName()).replace(enchant.getEnchantment().getColor() + enchant.getCustomName() + " ", ""));

        if (!useUnsafeEnchantments && level > enchant.getEnchantment().getMaxLevel()) level = enchant.getEnchantment().getMaxLevel();

        return level;
    }

    private String checkLevels(ItemStack item, String customName) {
        String line = "";

        if (methods.verifyItemLore(item)) {
            ItemMeta meta = item.getItemMeta();

            if (meta != null && meta.hasLore()) {
                List<String> itemLore = meta.getLore();

                if (itemLore != null) {
                    for (String lore : itemLore) {
                        if (lore.contains(customName)) {
                            line = lore;
                            break;
                        }
                    }
                }
            }
        }

        return line;
    }

    public int randomLevel(CEnchantment enchantment, Category category) {
        int enchantmentMax = enchantment.getMaxLevel(); // Max set by the enchantment.
        int randomLevel = 1 + random.nextInt(enchantmentMax);

        if (category.useMaxLevel()) {
            if (randomLevel > category.getMaxLevel()) randomLevel = 1 + random.nextInt(enchantmentMax);

            if (randomLevel < category.getMinLevel()) randomLevel = category.getMinLevel();

            if (randomLevel > enchantmentMax) randomLevel = enchantmentMax;
        }

        return randomLevel;
    }

    /**
     * @return The block list for blast.
     */
    public List<Material> getBlockList() {
        return blockList;
    }

    /**
     * @return If the blast enchantment drops blocks.
     */
    public boolean isDropBlocksBlast() {
        return dropBlocksBlast;
    }

    /**
     * @param dropBlocksBlast If the blast enchantment drops blocks.
     */
    public void setDropBlocksBlast(boolean dropBlocksBlast) {
        this.dropBlocksBlast = dropBlocksBlast;
    }

    public List<Event> getIgnoredEvents() {
        return ignoredEvents;
    }

    public boolean isIgnoredEvent(Event event) {
        return ignoredEvents.contains(event);
    }

    public void addIgnoredEvent(Event event) {
        if (!ignoredEvents.contains(event)) ignoredEvents.add(event);
    }

    public void removeIgnoredUUID(UUID uuid) {
        ignoredUUIDs.remove(uuid);
    }

    public List<UUID> getIgnoredUUIDs() {
        return ignoredUUIDs;
    }

    public boolean isIgnoredUUID(UUID uuid) {
        return ignoredUUIDs.contains(uuid);
    }

    public void addIgnoredUUID(UUID uuid) {
        if (!ignoredUUIDs.contains(uuid)) ignoredUUIDs.add(uuid);
    }

    public void removeIgnoredEvent(Event event) {
        ignoredEvents.remove(event);
    }

    /**
     * @return The max rage stack level.
     */
    public int getRageMaxLevel() {
        return rageMaxLevel;
    }

    /**
     * Set the max rage stack level.
     * @param level The new max stack level of the rage enchantment.
     */
    public void setRageMaxLevel(int level) {
        rageMaxLevel = level;
    }

    /**
     * Set if a player takes damage the current rage stack on the player will be lost.
     * @param toggle True if they lose the rage stack on damage and false if not.
     */
    public void setBreakRageOnDamage(boolean toggle) {
        breakRageOnDamage = toggle;
    }

    /**
     * Check if players lose their current rage stack on damage.
     * @return True if they do and false if not.
     */
    public boolean isBreakRageOnDamageOn() {
        return breakRageOnDamage;
    }

    /**
     * Check if players can enchant a stack of items with an enchantment book.
     */
    public boolean enchantStackedItems() {
        return enchantStackedItems;
    }

    /**
     * This converts an integer into a roman numeral if its between 1-10 otherwise it will just be the number as a string.
     * @param i The integer you want to convert.
     * @return The integer as a roman numeral if between 1-10 otherwise the number as a string.
     */
    public String convertLevelString(int i) {
        return switch (i) {
            case 0, 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> i + "";
        };
    }

    /**
     * This converts a String into a number if using a roman numeral from I-X.
     * @param i The string you want to convert.
     * @return The roman numeral as a number.
     */
    public int convertLevelInteger(String i) {
        switch (i) {
            case "I" -> {
                return 1;
            }

            case "II" -> {
                return 2;
            }

            case "III" -> {
                return 3;
            }

            case "IV" -> {
                return 4;
            }

            case "V" -> {
                return 5;
            }

            case "VI" -> {
                return 6;
            }

            case "VII" -> {
                return 7;
            }

            case "VIII" -> {
                return 8;
            }

            case "IX" -> {
                return 9;
            }

            case "X" -> {
                return 10;
            }

            default -> {
                if (methods.isInt(i)) {
                    return Integer.parseInt(i);
                } else {
                    return 0;
                }
            }
        }
    }

    private void addCEPlayer(CEPlayer player) {
        players.add(player);
    }

    private void removeCEPlayer(CEPlayer player) {
        players.remove(player);
    }

    private List<ItemStack> getInfoGKit(List<String> itemStrings) {
        List<ItemStack> items = new ArrayList<>();

        for (String itemString : itemStrings) {
            // This is used to convert old v1.7- gkit files to use newer way.
            itemString = getNewItemString(itemString);

            ItemBuilder itemBuilder = ItemBuilder.convertString(itemString);
            List<String> customEnchantments = new ArrayList<>();
            HashMap<Enchantment, Integer> enchantments = new HashMap<>();

            for (String option : itemString.split(", ")) {
                try {
                    Enchantment enchantment = methods.getEnchantment(option.split(":")[0]);
                    CEnchantment cEnchantment = getEnchantmentFromName(option.split(":")[0]);
                    String level = option.split(":")[1];

                    if (enchantment != null) {
                        if (level.contains("-")) {
                            customEnchantments.add("&7" + option.split(":")[0] + " " + level);
                        } else {
                            enchantments.put(enchantment, Integer.parseInt(level));
                        }
                    } else if (cEnchantment != null) {
                        customEnchantments.add(cEnchantment.getColor() + cEnchantment.getCustomName() + " " + level);
                    }
                } catch (Exception ignore) {}
            }

            itemBuilder.getLore().addAll(0, customEnchantments);
            itemBuilder.setEnchantments(enchantments);

            NBTItem nbtItem = new NBTItem(itemBuilder.build());
            // This is done so items do not stack if there are multiple of the same.

            nbtItem.setInteger("random-number", random.nextInt(Integer.MAX_VALUE));
            items.add(nbtItem.getItem());
        }

        return items;
    }

    public String getNewItemString(String itemString) {
        StringBuilder newItemString = new StringBuilder();

        for (String option : itemString.split(", ")) {
            if (option.toLowerCase().startsWith("enchantments:") || option.toLowerCase().startsWith("customenchantments:")) {
                StringBuilder newOption = new StringBuilder();

                for (String enchantment : option.toLowerCase().replace("customenchantments:", "").replace("enchantments:", "").split(",")) {
                    newOption.append(enchantment).append(", ");
                }

                option = newOption.substring(0, newOption.length() - 2);
            }

            newItemString.append(option).append(", ");
        }

        if (newItemString.length() > 0) itemString = newItemString.substring(0, newItemString.length() - 2);
        return itemString;
    }

    public int pickLevel(int min, int max) {
        return min + random.nextInt((max + 1) - min);
    }

    private List<Color> getColors(String string) {
        List<Color> colors = new ArrayList<>();
        methods.checkString(colors, string, methods);

        return colors;
    }

    public BlackSmithManager getBlackSmithManager() {
        return blackSmithManager;
    }

    public InfoMenuManager getInfoMenuManager() {
        return infoMenuManager;
    }
}