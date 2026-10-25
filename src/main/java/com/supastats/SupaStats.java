package com.supastats;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.NamespacedKey;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SupaStats extends JavaPlugin implements Listener {
    private final Map<UUID, Map<String, Long>> stats = new HashMap<>();
    private final Map<UUID, String> knownNames = new HashMap<>();
    private final Map<UUID, Map<String, String>> offlineValues = new HashMap<>();
    private File statsFile;
    private YamlConfiguration statsYaml;
    private File offlineFile;
    private YamlConfiguration offlineYaml;
    private File guiFile;
    private YamlConfiguration guiYaml;
    private Economy economy;
    private static final String[] COUNTERS = {"kills", "deaths", "blocks_placed", "blocks_broken", "mobs_killed"};
    private static final Pattern AMPERSAND = Pattern.compile("&([0-9a-fk-or])", Pattern.CASE_INSENSITIVE);
    private static final Map<Character, String> MINI_COLORS = Map.ofEntries(
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"), Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"), Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"), Map.entry('7', "<gray>"), Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"), Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"), Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"), Map.entry('l', "<bold>"), Map.entry('o', "<italic>"),
            Map.entry('n', "<underlined>"), Map.entry('m', "<strikethrough>"), Map.entry('k', "<obfuscated>"),
            Map.entry('r', "<reset>")
    );

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("gui.yml", false);
        guiFile = new File(getDataFolder(), "gui.yml");
        guiYaml = YamlConfiguration.loadConfiguration(guiFile);
        statsFile = new File(getDataFolder(), "stats.yml");
        statsYaml = YamlConfiguration.loadConfiguration(statsFile);
        offlineFile = new File(getDataFolder(), "offline.yml");
        offlineYaml = YamlConfiguration.loadConfiguration(offlineFile);
        loadData();
        setupEconomy();
        Bukkit.getPluginManager().registerEvents(this, this);
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new StatsExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered: %statistics_<stat>%");
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            remember(player);
        }
        getLogger().info("SupaStats enabled.");
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault not found; economy balance will show N/A.");
            return;
        }
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider != null) economy = provider.getProvider();
        if (economy == null) getLogger().warning("No Vault economy provider found; install an economy plugin such as EssentialsX Economy or another Vault-compatible economy.");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    private void loadData() {
        ConfigurationSection root = statsYaml.getConfigurationSection("players");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(id);
                    Map<String, Long> values = new HashMap<>();
                    for (String key : COUNTERS) values.put(key, statsYaml.getLong("players." + id + "." + key, 0));
                    stats.put(uuid, values);
                    String name = statsYaml.getString("players." + id + ".name");
                    if (name != null) knownNames.put(uuid, name);
                } catch (IllegalArgumentException ignored) {
                    getLogger().warning("Skipping invalid UUID in stats.yml: " + id);
                }
            }
        }
        ConfigurationSection offlineRoot = offlineYaml.getConfigurationSection("players");
        if (offlineRoot != null) {
            for (String id : offlineRoot.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(id);
                    Map<String, String> values = new HashMap<>();
                    for (String key : new String[]{"money", "shards", "playtime"})
                        values.put(key, offlineYaml.getString("players." + id + "." + key, "N/A"));
                    offlineValues.put(uuid, values);
                } catch (IllegalArgumentException ignored) { }
            }
        }
    }

    private void saveData() {
        statsYaml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Long>> entry : stats.entrySet()) {
            String path = "players." + entry.getKey();
            statsYaml.set(path + ".name", knownNames.getOrDefault(entry.getKey(), ""));
            for (String key : COUNTERS) statsYaml.set(path + "." + key, entry.getValue().getOrDefault(key, 0L));
        }
        saveYaml(statsYaml, statsFile);
        offlineYaml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, String>> entry : offlineValues.entrySet()) {
            String path = "players." + entry.getKey();
            entry.getValue().forEach((key, value) -> offlineYaml.set(path + "." + key, value));
        }
        saveYaml(offlineYaml, offlineFile);
    }

    private void saveYaml(YamlConfiguration yaml, File file) {
        try { yaml.save(file); } catch (IOException exception) {
            getLogger().severe("Could not save " + file.getName() + ": " + exception.getMessage());
        }
    }

    private Map<String, Long> values(UUID uuid) {
        return stats.computeIfAbsent(uuid, ignored -> {
            Map<String, Long> initial = new HashMap<>();
            for (String key : COUNTERS) initial.put(key, 0L);
            return initial;
        });
    }

    private long count(UUID uuid, String key) { return values(uuid).getOrDefault(key, 0L); }
    private void increment(UUID uuid, String key) { values(uuid).put(key, count(uuid, key) + 1); }

    private void remember(Player player) {
        knownNames.put(player.getUniqueId(), player.getName());
        offlineValues.remove(player.getUniqueId());
        saveData();
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { remember(event.getPlayer()); }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Map<String, String> saved = new HashMap<>();
        saved.put("money", economy == null ? "N/A" : formatMoney(economy.getBalance(player)));
        saved.put("shards", papi(player, "coinsengine_balance_short_shards"));
        saved.put("playtime", papi(player, "statistic_time_played"));
        offlineValues.put(player.getUniqueId(), saved);
        knownNames.put(player.getUniqueId(), player.getName());
        saveData();
    }

    private String papi(Player player, String key) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return "N/A";
        return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%" + key + "%");
    }

    @EventHandler public void onDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (victim instanceof Player player) {
            increment(player.getUniqueId(), "deaths");
            if (killer != null) increment(killer.getUniqueId(), "kills");
        } else if (killer != null) {
            increment(killer.getUniqueId(), "mobs_killed");
        }
    }

    @EventHandler public void onPlace(BlockPlaceEvent event) {
        if (!event.isCancelled()) increment(event.getPlayer().getUniqueId(), "blocks_placed");
    }

    @EventHandler public void onBreak(BlockBreakEvent event) {
        if (!event.isCancelled()) increment(event.getPlayer().getUniqueId(), "blocks_broken");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(plain(message("player-only")));
            return true;
        }
        if (!viewer.hasPermission("supastats.view")) {
            viewer.sendMessage(plain(message("no-permission")));
            return true;
        }
        if (!getConfig().getBoolean("settings.gui-enabled", true)) {
            viewer.sendMessage(plain(message("gui-disabled")));
            return true;
        }
        OfflinePlayer target;
        if (args.length > 0) {
            if (!viewer.hasPermission("supastats.view.others")) {
                viewer.sendMessage(plain(message("no-permission")));
                return true;
            }
            target = findPlayer(args[0]);
            if (target == null || (!target.hasPlayedBefore() && !target.isOnline() && !knownNames.containsValue(args[0]))) {
                viewer.sendMessage(plain(message("player-not-found")));
                return true;
            }
        } else target = viewer;
        openStats(viewer, target);
        return true;
    }

    private OfflinePlayer findPlayer(String name) {
        for (Map.Entry<UUID, String> entry : knownNames.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) return Bukkit.getOfflinePlayer(entry.getKey());
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        if (offline != null && (offline.hasPlayedBefore() || offline.isOnline())) return offline;
        Player online = Bukkit.getPlayerExact(name);
        return online;
    }

    private void openStats(Player viewer, OfflinePlayer target) {
        int rows = Math.max(1, Math.min(6, getConfig().getInt("settings.rows", 4)));
        String playerName = target.getName() == null ? "Unknown" : target.getName();
        String title = target.getUniqueId().equals(viewer.getUniqueId())
                ? getConfig().getString("settings.gui-title", "<#3F3F3F>ꜱᴛᴀᴛꜱ")
                : getConfig().getString("settings.other-player-title", "<#3F3F3F>{player} ꜱᴛᴀᴛꜱ").replace("{player}", playerName);
        Inventory inventory = Bukkit.createInventory(null, rows * 9, component(title));
        Map<String, String> placeholders = getPlaceholders(target);
        ConfigurationSection items = guiYaml.getConfigurationSection("items");
        if (items != null) {
            for (String id : items.getKeys(false)) {
                ConfigurationSection item = items.getConfigurationSection(id);
                if (item == null || !item.getBoolean("enabled", true)) continue;
                int slot = item.getInt("slot", -1);
                if (slot < 0 || slot >= inventory.getSize()) continue;
                Material material = Material.matchMaterial(item.getString("material", "PAPER"));
                if (material == null || !material.isItem()) material = Material.PAPER;
                ItemStack stack = new ItemStack(material);
                ItemMeta meta = stack.getItemMeta();
                String name = replace(item.getString("name", id), placeholders);
                meta.displayName(component(name));
                ArrayList<Component> lore = new ArrayList<>();
                for (String line : item.getStringList("lore")) lore.add(component(replace(line, placeholders)));
                meta.lore(lore);
                meta.setItemName(null);
                if (item.getBoolean("hide-attributes", false)) meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                stack.setItemMeta(meta);
                inventory.setItem(slot, stack);
            }
        }
        viewer.openInventory(inventory);
    }

    private Map<String, String> getPlaceholders(OfflinePlayer target) {
        UUID uuid = target.getUniqueId();
        Map<String, String> result = new HashMap<>();
        result.put("player", target.getName() == null ? "Unknown" : target.getName());
        for (String key : COUNTERS) result.put(key, format(count(uuid, key)));
        Player online = target.getPlayer();
        Map<String, String> saved = offlineValues.getOrDefault(uuid, Map.of());
        result.put("money", economy == null ? "N/A" : formatMoney(economy.getBalance(target)));
        result.put("shards", online == null ? saved.getOrDefault("shards", "N/A") : papi(online, "coinsengine_balance_short_shards"));
        result.put("playtime", online == null ? saved.getOrDefault("playtime", "N/A") : papi(online, "statistic_time_played"));
        return result;
    }

    private String format(long number) {
        return NumberFormat.getNumberInstance(Locale.US).format(number);
    }

    private String formatMoney(double amount) {
        return NumberFormat.getNumberInstance(Locale.US).format(amount);
    }

    private String replace(String input, Map<String, String> placeholders) {
        String output = input == null ? "" : input;
        for (Map.Entry<String, String> entry : placeholders.entrySet())
            output = output.replace("{" + entry.getKey() + "}", entry.getValue());
        return output;
    }

    private String message(String key) { return getConfig().getString("messages." + key, "<red>Something went wrong."); }

    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().title().toString().toLowerCase(Locale.ROOT).contains("ꜱᴛᴀᴛꜱ")) event.setCancelled(true);
    }

    private Component component(String text) {
        String value = text == null ? "" : text;
        Matcher matcher = AMPERSAND.matcher(value);
        StringBuffer converted = new StringBuffer();
        while (matcher.find()) {
            String tag = MINI_COLORS.get(Character.toLowerCase(matcher.group(1).charAt(0)));
            matcher.appendReplacement(converted, Matcher.quoteReplacement(tag == null ? matcher.group() : tag));
        }
        matcher.appendTail(converted);
        // Never apply italics by default; only explicit MiniMessage <italic> or legacy &o enables it.
        converted.insert(0, "<!italic>");
        return MiniMessage.miniMessage().deserialize(converted.toString());
    }

    private String plain(String text) {
        return ChatColor.stripColor(text == null ? "" : ChatColor.translateAlternateColorCodes('&', text));
    }

    private static final class StatsExpansion extends PlaceholderExpansion {
        private final SupaStats plugin;
        private StatsExpansion(SupaStats plugin) { this.plugin = plugin; }
        @Override public String getIdentifier() { return "statistics"; }
        @Override public String getAuthor() { return "univer404"; }
        @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
        @Override public boolean persist() { return true; }
        @Override public String onRequest(OfflinePlayer player, String params) {
            if (player == null) return "0";
            String key = params.toLowerCase(Locale.ROOT);
            if (!java.util.Arrays.asList(COUNTERS).contains(key)) return null;
            return plugin.format(plugin.count(player.getUniqueId(), key));
        }
    }
}
