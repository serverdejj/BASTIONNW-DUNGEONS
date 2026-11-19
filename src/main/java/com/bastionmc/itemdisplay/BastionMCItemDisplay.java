package com.bastionmc.itemdisplay;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import com.google.common.collect.Multimap;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.*;

public final class BastionMCItemDisplay extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private NamespacedKey rarityKey;
    private final Map<UUID, TextDisplay> labels = new HashMap<>();
    private final Map<UUID, TextDisplay> detailLabels = new HashMap<>();
    private final Map<UUID, List<TextDisplay>> detailExtras = new HashMap<>();
    private final Map<UUID, Set<UUID>> detailViewers = new HashMap<>();
    private final Map<UUID, String> glowTeams = new HashMap<>();
    // Store every scoreboard touched so we can remove our teams safely on reload/disable.
    private final Set<Scoreboard> managedGlowBoards = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<UUID, Boolean> oldGlow = new HashMap<>();
    private final List<BukkitTask> tasks = new ArrayList<>();
    private static final List<String> RARITIES = List.of("comun", "pococomun", "raro", "muyraro", "epico", "legendario", "mitico", "divino");
    private static final Pattern HEX = Pattern.compile("&#([0-9a-fA-F]{6})");
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();

    @Override public void onEnable() {
        saveDefaultConfig();
        rarityKey = new NamespacedKey(this, "rarity");
        getServer().getPluginManager().registerEvents(this, this);
        for (String name : List.of("rareza", "itemdisplay")) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) { cmd.setExecutor(this); cmd.setTabCompleter(this); }
        }
        startTasks();
        getLogger().info("BastionMCItemDisplay beta habilitado. Probar en Purpur 1.21.11.");
    }
    @Override public void onDisable() { stopTasks(); clearLabels(); clearDetails(); clearTeams(); }
    private void stopTasks() { tasks.forEach(BukkitTask::cancel); tasks.clear(); }
    private void startTasks() {
        stopTasks();
        long labelsPeriod = Math.max(1L, getConfig().getLong("display.update-ticks", 1));
        long targetPeriod = Math.max(1L, getConfig().getLong("target-info.update-ticks", 2));
        long effectsPeriod = Math.max(5L, getConfig().getLong("effects.update-ticks", 20));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, this::updateLabels, 1L, labelsPeriod));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, this::updateTargets, 3L, targetPeriod));
        tasks.add(Bukkit.getScheduler().runTaskTimer(this, this::updateEffects, 10L, effectsPeriod));
    }
    private Component colored(String input) {
        Matcher matcher = HEX.matcher(input == null ? "" : input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder seq = new StringBuilder("§x");
            for (char c : hex.toCharArray()) seq.append('§').append(c);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(seq.toString()));
        }
        matcher.appendTail(buffer);
        return serializer.deserialize(ChatColor.translateAlternateColorCodes('&', buffer.toString()));
    }
    private void send(CommandSender sender, String path) { sender.sendMessage(colored(getConfig().getString("messages.prefix", "") + getConfig().getString("messages." + path, ""))); }
    private String customRarity(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        String value = stack.getItemMeta().getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
        return RARITIES.contains(value) ? value : null;
    }
    private String rarity(ItemStack stack) {
        String custom = customRarity(stack);
        if (custom != null) return custom;
        // Reflectively read the native rarity if exposed by the current Paper API.
        for (Object source : new Object[]{stack, stack.getItemMeta(), stack.getType()}) {
            if (source == null) continue;
            for (String method : List.of("getRarity", "getItemRarity")) {
                try {
                    Method m = source.getClass().getMethod(method);
                    Object result = m.invoke(source);
                    if (result != null) {
                        String r = result.toString().toLowerCase(Locale.ROOT).replace("_", "");
                        if (r.equals("common")) return "comun";
                        if (r.equals("uncommon")) return "pococomun";
                        if (r.equals("rare")) return "raro";
                        if (r.equals("epic")) return "epico";
                    }
                } catch (ReflectiveOperationException | SecurityException ignored) { }
            }
        }
        // Fallback for servers whose API does not expose the rarity component.
        return switch (stack.getType()) {
            case TOTEM_OF_UNDYING, NETHER_STAR, ENCHANTED_GOLDEN_APPLE -> "pococomun";
            case END_CRYSTAL -> "raro";
            case DRAGON_EGG -> "epico";
            default -> "comun";
        };
    }
    private Component itemName(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) return meta.displayName();
        // Native item translation with vanilla rarity color, independent of custom rarity.
        String nativeRarity = nativeRarity(stack);
        NamedTextColor color = switch (nativeRarity) {
            case "pococomun" -> NamedTextColor.YELLOW;
            case "raro" -> NamedTextColor.AQUA;
            case "epico" -> NamedTextColor.LIGHT_PURPLE;
            default -> NamedTextColor.WHITE;
        };
        return Component.translatable(stack.getType().translationKey()).color(color);
    }
    private String nativeRarity(ItemStack stack) {
        // Custom PDC rarity must never alter the original item's name color.
        ItemStack clone = stack.clone();
        if (clone.hasItemMeta()) {
            ItemMeta meta = clone.getItemMeta();
            meta.getPersistentDataContainer().remove(rarityKey);
            clone.setItemMeta(meta);
        }
        return rarity(clone);
    }
    // ItemStacker 1.1.0 maintains a virtual group total which can exceed the
    // physical ItemStack amount. Reflection keeps ItemStacker an optional dependency.
    private long displayedAmount(Item item) {
        long physical = item.getItemStack().getAmount();
        if (!getConfig().getBoolean("itemstacker.enabled", true)
                || !Bukkit.getPluginManager().isPluginEnabled("ItemStacker")) return physical;
        try {
            Class<?> managerClass = Class.forName("me.itemstacker.managers.Manager");
            Object manager = managerClass.getMethod("get").invoke(null);
            if (manager == null) return physical;
            Object group = managerClass.getMethod("group", int.class).invoke(manager, item.getEntityId());
            if (group == null) return physical;
            java.lang.reflect.Field field = group.getClass().getDeclaredField("total");
            field.setAccessible(true);
            long total = field.getLong(group);
            return total > 0 ? Math.max(physical, total) : physical;
        } catch (ReflectiveOperationException | LinkageError | SecurityException ex) {
            return physical;
        }
    }
    private Component label(ItemStack stack, long amount) {
        Component name = itemName(stack);
        if (getConfig().getBoolean("display.show-amount", true) && (amount > 1 || getConfig().getBoolean("display.show-single-amount", false)))
            name = name.append(colored(getConfig().getString("display.amount-format", " &7x%amount%").replace("%amount%", Long.toString(amount))));
        return name;
    }
    private boolean isNearby(Item item, double range) {
        double maxSq = range * range;
        for (Player player : item.getWorld().getPlayers())
            if (player.getLocation().distanceSquared(item.getLocation()) <= maxSq) return true;
        return false;
    }
    private void updateLabels() {
        if (!getConfig().getBoolean("display.enabled", true)) { clearLabels(); return; }
        Set<UUID> active = new HashSet<>();
        double range = getConfig().getDouble("display.max-distance", 32);
        for (World world : Bukkit.getWorlds()) for (Item item : world.getEntitiesByClass(Item.class)) {
            if (!item.isValid() || !isNearby(item, range)) continue;
            UUID id = item.getUniqueId(); active.add(id);
            TextDisplay display = labels.get(id);
            if (display == null || !display.isValid()) {
                display = world.spawn(item.getLocation().add(0, getConfig().getDouble("display.height-offset", 0.35), 0), TextDisplay.class, entity -> {
                    entity.setPersistent(false);
                    entity.setBillboard(Display.Billboard.CENTER);
                    entity.setShadowed(getConfig().getBoolean("display.shadow", true));
                    entity.setSeeThrough(getConfig().getBoolean("display.see-through", false));
                    entity.setDefaultBackground(false);
                    entity.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                    entity.setInvulnerable(true);
                    entity.setTeleportDuration(1);
                    entity.setInterpolationDuration(1);
                });
                labels.put(id, display);
            }
            display.teleport(item.getLocation().add(0, getConfig().getDouble("display.height-offset", 0.35), 0));
            scale(display, getConfig().getDouble("display.scale", 0.65));
            display.text(label(item.getItemStack(), displayedAmount(item)));
        }
        for (UUID id : new HashSet<>(labels.keySet())) if (!active.contains(id)) removeLabel(id);
    }
    private void scale(TextDisplay d, double value) {
        float v = (float)Math.max(0.1, Math.min(3, value));
        Transformation t = d.getTransformation();
        if (Math.abs(t.getScale().x - v) > 0.001) { t.getScale().set(v, v, v); d.setTransformation(t); }
    }
    private void removeLabel(UUID id) {
        removeDetail(id);
        TextDisplay display = labels.remove(id);
        if (display != null && display.isValid()) display.remove();
    }
    private void clearLabels() { for (UUID id : new HashSet<>(labels.keySet())) removeLabel(id); }
    @EventHandler public void onItemDespawn(ItemDespawnEvent event) { removeLabel(event.getEntity().getUniqueId()); restoreGlow(event.getEntity()); }
    @EventHandler public void onItemPickup(EntityPickupItemEvent event) { Bukkit.getScheduler().runTask(this, () -> {
        Item item = event.getItem(); if (!item.isValid()) removeLabel(item.getUniqueId());
    }); }
    private void updateEffects() {
        if (!getConfig().getBoolean("effects.enabled", true)) { restoreGlow(); return; }
        double range = getConfig().getDouble("effects.max-distance", 16);
        int count = Math.max(0, Math.min(20, getConfig().getInt("effects.particle-count", 3)));
        Set<UUID> seen = new HashSet<>();
        for (World world : Bukkit.getWorlds()) for (Item item : world.getEntitiesByClass(Item.class)) {
            if (!item.isValid()) continue;
            UUID id = item.getUniqueId();
            String key = rarity(item.getItemStack());
            boolean glow = getConfig().getBoolean("rarities." + key + ".glowing", false);
            if (glow) {
                seen.add(id);
                if (!oldGlow.containsKey(id)) oldGlow.put(id, item.isGlowing());
                item.setGlowing(true);
                assignGlowTeam(item, key);
            } else if (oldGlow.containsKey(id)) restoreGlow(item);
            if (!isNearby(item, range) || count == 0 || !getConfig().getBoolean("rarities." + key + ".particles", false)) continue;
            try {
                String hex = getConfig().getString("rarities." + key + ".color", "#FFFFFF").replace("#", "");
                java.awt.Color rgb = java.awt.Color.decode("#" + hex);
                Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(rgb.getRed(), rgb.getGreen(), rgb.getBlue()), 1.0f);
                world.spawnParticle(Particle.DUST, item.getLocation().add(0, 0.18, 0), count, 0.18, 0.16, 0.18, 0, dust);
            } catch (IllegalArgumentException ignored) { }
        }
        for (UUID id : new HashSet<>(oldGlow.keySet())) if (!seen.contains(id)) {
            Entity entity = Bukkit.getEntity(id);
            if (entity instanceof Item item) restoreGlow(item);
            else oldGlow.remove(id);
        }
    }
    private String glowTeamName(String rarity) {
        return "bid_" + rarity.substring(0, Math.min(10, rarity.length()));
    }
    private Set<Scoreboard> viewerScoreboards() {
        Set<Scoreboard> boards = Collections.newSetFromMap(new IdentityHashMap<>());
        boards.add(Bukkit.getScoreboardManager().getMainScoreboard());
        for (Player player : Bukkit.getOnlinePlayers()) boards.add(player.getScoreboard());
        return boards;
    }
    private void assignGlowTeam(Item item, String rarity) {
        // The entity's outline color comes from the VIEWER's scoreboard team.
        // Most sidebar plugins give each player a separate scoreboard, so the main one alone is insufficient.
        if (!getConfig().getBoolean("effects.colored-glow", true)) return;
        String teamName = glowTeamName(rarity);
        String entry = item.getUniqueId().toString();
        String old = glowTeams.put(item.getUniqueId(), teamName);
        String hex = getConfig().getString("rarities." + rarity + ".color", "#FFFFFF");
        net.kyori.adventure.text.format.TextColor parsed = net.kyori.adventure.text.format.TextColor.fromHexString(hex);
        NamedTextColor nearest = parsed == null ? NamedTextColor.WHITE : NamedTextColor.nearestTo(parsed);
        for (Scoreboard board : viewerScoreboards()) {
            managedGlowBoards.add(board);
            Team team = board.getTeam(teamName);
            if (team == null) team = board.registerNewTeam(teamName);
            team.color(nearest);
            // An entry cannot belong to two teams on the same board.
            if (old != null && !old.equals(teamName)) {
                Team previous = board.getTeam(old);
                if (previous != null) previous.removeEntry(entry);
            }
            if (!team.hasEntry(entry)) team.addEntry(entry);
        }
    }
    private void clearTeam(Item item) {
        String name = glowTeams.remove(item.getUniqueId());
        if (name == null) return;
        for (Scoreboard board : managedGlowBoards) {
            Team team = board.getTeam(name);
            if (team != null) team.removeEntry(item.getUniqueId().toString());
        }
    }
    private void clearTeams() {
        for (Scoreboard board : managedGlowBoards) {
            for (Map.Entry<UUID, String> e : glowTeams.entrySet()) {
                Team team = board.getTeam(e.getValue());
                if (team != null) team.removeEntry(e.getKey().toString());
            }
            for (String rarity : RARITIES) {
                Team team = board.getTeam(glowTeamName(rarity));
                if (team != null && team.getEntries().isEmpty()) team.unregister();
            }
        }
        glowTeams.clear();
        managedGlowBoards.clear();
    }
    private void restoreGlow(Item item) { clearTeam(item); Boolean prior = oldGlow.remove(item.getUniqueId()); if (prior != null && item.isValid()) item.setGlowing(prior); }
    private void restoreGlow() { for (UUID id : new HashSet<>(oldGlow.keySet())) {
        Entity e = Bukkit.getEntity(id); if (e instanceof Item item) restoreGlow(item); else oldGlow.remove(id);
    } }
    private void removeDetail(UUID id) {
        TextDisplay d = detailLabels.remove(id);
        if (d != null && d.isValid()) d.remove();
        for (TextDisplay extra : detailExtras.getOrDefault(id, List.of())) if (extra.isValid()) extra.remove();
        detailExtras.remove(id);
        detailViewers.remove(id);
    }
    private void clearDetails() { for (UUID id : new HashSet<>(detailLabels.keySet())) removeDetail(id); }
    private void updateTargets() {
        if (!getConfig().getBoolean("target-info.enabled", true)) { clearDetails(); return; }
        double maxDistance = Math.max(1, getConfig().getDouble("target-info.max-distance", 5));
        double proximity = Math.max(0, getConfig().getDouble("target-info.proximity-distance", 1));
        double sneakDistance = Math.max(0, getConfig().getDouble("target-info.sneak-distance", 3));
        Map<UUID, Set<UUID>> visible = new HashMap<>();
        Map<UUID, Item> relevant = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location eye = player.getEyeLocation();
            RayTraceResult hit = player.getWorld().rayTraceEntities(eye, eye.getDirection(), maxDistance, 0.25,
                entity -> entity instanceof Item && entity.isValid());
            Item aimed = hit != null && hit.getHitEntity() instanceof Item i ? i : null;
            for (Item item : player.getWorld().getEntitiesByClass(Item.class)) {
                if (!item.isValid()) continue;
                double dist = player.getLocation().distanceSquared(item.getLocation());
                boolean selected = item.equals(aimed) || (proximity > 0 && dist <= proximity * proximity)
                    || (player.isSneaking() && sneakDistance > 0 && dist <= sneakDistance * sneakDistance);
                if (!selected) continue;
                UUID id = item.getUniqueId();
                relevant.put(id, item);
                visible.computeIfAbsent(id, k -> new HashSet<>()).add(player.getUniqueId());
            }
        }
        for (Map.Entry<UUID, Item> entry : relevant.entrySet()) {
            UUID id = entry.getKey(); Item item = entry.getValue();
            TextDisplay d = detailLabels.get(id);
            if (d == null || !d.isValid()) {
                d = item.getWorld().spawn(item.getLocation(), TextDisplay.class, entity -> {
                    entity.setPersistent(false);
                    entity.setBillboard(Display.Billboard.CENTER);
                    entity.setDefaultBackground(false);
                    entity.setBackgroundColor(Color.fromARGB(0,0,0,0));
                    entity.setShadowed(true);
                    entity.setTeleportDuration(1);
                    entity.setInterpolationDuration(1);
                });
                detailLabels.put(id, d);
                for (Player viewer : Bukkit.getOnlinePlayers()) viewer.hideEntity(this, d);
            }
            List<Component> lines = infoParts(item.getItemStack(), displayedAmount(item));
            // Separate TextDisplays permit a smaller font for enchantment lines.
            List<TextDisplay> extras = detailExtras.computeIfAbsent(id, ignored -> new ArrayList<>());
            while (extras.size() < lines.size() - 1) {
                TextDisplay extra = item.getWorld().spawn(item.getLocation(), TextDisplay.class, entity -> {
                    entity.setPersistent(false);
                    entity.setBillboard(Display.Billboard.CENTER);
                    entity.setDefaultBackground(false);
                    entity.setBackgroundColor(Color.fromARGB(0,0,0,0));
                    entity.setShadowed(true);
                    entity.setTeleportDuration(1);
                    entity.setInterpolationDuration(1);
                });
                for (Player viewer : Bukkit.getOnlinePlayers()) viewer.hideEntity(this, extra);
                extras.add(extra);
            }
            while (extras.size() > lines.size() - 1) {
                TextDisplay old = extras.remove(extras.size() - 1);
                if (old.isValid()) old.remove();
            }
            double baseHeight = getConfig().getDouble("target-info.height-offset", 0.75);
            double spacing = getConfig().getDouble("target-info.line-spacing", 0.17);
            d.teleport(item.getLocation().add(0, baseHeight + spacing * (lines.size() - 1), 0));
            scale(d, getConfig().getDouble("target-info.scale", 0.48));
            d.text(lines.get(0));
            for (int line = 1; line < lines.size(); line++) {
                TextDisplay extra = extras.get(line - 1);
                extra.teleport(item.getLocation().add(0, baseHeight + spacing * (lines.size() - 1 - line), 0));
                scale(extra, getConfig().getDouble("target-info.enchantments.scale", 0.32));
                extra.text(lines.get(line));
            }
            Set<UUID> viewers = visible.get(id);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (viewers.contains(player.getUniqueId())) {
                    player.showEntity(this, d);
                    for (TextDisplay extra : extras) player.showEntity(this, extra);
                } else {
                    player.hideEntity(this, d);
                    for (TextDisplay extra : extras) player.hideEntity(this, extra);
                }
            }
            detailViewers.put(id, viewers);
        }
        for (UUID id : new HashSet<>(detailLabels.keySet())) if (!relevant.containsKey(id)) removeDetail(id);
    }
    private String roman(int n) {
        if (!getConfig().getBoolean("enchantments.use-roman-numerals", true) || n <= 0 || n > 3999) return Integer.toString(n);
        StringBuilder out = new StringBuilder();
        int[] values = {1000,900,500,400,100,90,50,40,10,9,5,4,1};
        String[] glyphs = {"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};
        for (int i=0; i<values.length; i++) while (n>=values[i]) { out.append(glyphs[i]); n-=values[i]; }
        return out.toString();
    }
    private String enchantmentName(String key) {
        String path = "enchantments.names." + key;
        if (getConfig().contains(path)) return getConfig().getString(path, key);
        return key.replace('_', ' ');
    }
    private double armorPoints(ItemStack stack) {
        // Vanilla material protection. Can be overridden for custom items in config.
        String material = stack.getType().name();
        String slot;
        if (material.endsWith("_HELMET") || material.equals("TURTLE_HELMET")) slot="helmet";
        else if (material.endsWith("_CHESTPLATE") || material.equals("ELYTRA")) slot="chestplate";
        else if (material.endsWith("_LEGGINGS")) slot="leggings";
        else if (material.endsWith("_BOOTS")) slot="boots";
        else return 0;
        if (getConfig().contains("armor-display.material-overrides." + material))
            return Math.max(0, getConfig().getDouble("armor-display.material-overrides." + material));
        String tier = material.split("_")[0];
        if (material.equals("TURTLE_HELMET")) return 2;
        if (material.equals("ELYTRA")) return 0;
        return switch (tier) {
            case "LEATHER" -> switch(slot) {case "helmet"->1;case "chestplate"->3;case "leggings"->2;default->1;};
            case "GOLDEN" -> switch(slot) {case "helmet"->2;case "chestplate"->5;case "leggings"->3;default->1;};
            case "CHAINMAIL" -> switch(slot) {case "helmet"->2;case "chestplate"->5;case "leggings"->4;default->1;};
            case "IRON" -> switch(slot) {case "helmet"->2;case "chestplate"->6;case "leggings"->5;default->2;};
            case "DIAMOND", "NETHERITE" -> switch(slot) {case "helmet"->3;case "chestplate"->8;case "leggings"->6;default->3;};
            default -> 0;
        };
    }
    private Component armorIcons(double points) {
        int max = Math.max(1, getConfig().getInt("armor-display.max-icons", 10));
        String full = getConfig().getString("armor-display.icons.full", "■");
        String half = getConfig().getString("armor-display.icons.half", "◧");
        String empty = getConfig().getString("armor-display.icons.empty", "□");
        String fullColor = getConfig().getString("armor-display.colors.full", "&7");
        String halfColor = getConfig().getString("armor-display.colors.half", "&7");
        String emptyColor = getConfig().getString("armor-display.colors.empty", "&8");
        Component result = Component.empty();
        for (int i=0;i<max;i++) {
            double remaining = points - i*2;
            if (remaining >= 2) result = result.append(colored(fullColor + full));
            else if (remaining >= 1) result = result.append(colored(halfColor + half));
            else if (getConfig().getBoolean("armor-display.show-empty-icons", false)) result = result.append(colored(emptyColor + empty));
        }
        return result;
    }
    private boolean isArmorItem(ItemStack stack) {
        String material = stack.getType().name();
        return material.endsWith("_HELMET") || material.endsWith("_CHESTPLATE")
            || material.endsWith("_LEGGINGS") || material.endsWith("_BOOTS")
            || material.equals("TURTLE_HELMET") || material.equals("ELYTRA")
            || material.equals("CARVED_PUMPKIN") || material.equals("PLAYER_HEAD")
            || material.endsWith("_HEAD") || material.endsWith("_SKULL");
    }
    private List<Component> infoParts(ItemStack stack, long amount) {
        List<Component> parts = new ArrayList<>();
        if (getConfig().getBoolean("target-info.show-name", true)) parts.add(itemName(stack));
        String r = rarity(stack);
        if (getConfig().getBoolean("target-info.show-rarity", true)) parts.add(colored(getConfig().getString("rarities." + r + ".label", r)));
        if (getConfig().getBoolean("target-info.show-enchantments", true) && !stack.getEnchantments().isEmpty()) {
            int perLine = Math.max(1, getConfig().getInt("target-info.enchantments.per-line", 2));
            String separator = getConfig().getString("target-info.enchantments.separator", " · ");
            List<String> enchants = new ArrayList<>();
            stack.getEnchantments().forEach((enchant, level) ->
                enchants.add(enchantmentName(enchant.getKey().getKey()) + " " + roman(level)));
            for (int i = 0; i < enchants.size(); i += perLine) {
                parts.add(colored(getConfig().getString("enchantments.color", "&b") +
                    String.join(separator, enchants.subList(i, Math.min(i + perLine, enchants.size())))));
            }
        }
        if (getConfig().getBoolean("durability-display.enabled", true) && isArmorItem(stack) && stack.getType().getMaxDurability() > 0) {
            org.bukkit.inventory.meta.Damageable damageable = stack.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable d ? d : null;
            int max = stack.getType().getMaxDurability();
            if (damageable != null && damageable.hasMaxDamage()) max = damageable.getMaxDamage();
            if (max > 0) {
                int damage = damageable == null ? 0 : Math.max(0, damageable.getDamage());
                int percent = (int) Math.round(100.0 * Math.max(0, max - damage) / max);
                int high = getConfig().getInt("durability-display.thresholds.high", 75);
                int low = getConfig().getInt("durability-display.thresholds.low", 25);
                String color = percent >= high ? getConfig().getString("durability-display.colors.high", "&a")
                        : percent <= low ? getConfig().getString("durability-display.colors.low", "&c")
                        : getConfig().getString("durability-display.colors.medium", "&e");
                String format = getConfig().getString("durability-display.format", "&7Durabilidad: %color%%percent%%");
                parts.add(colored(format.replace("%color%", color).replace("%percent%", Integer.toString(percent))));
            }
        }
        if (getConfig().getBoolean("armor-display.enabled", false)) {
            double armor = armorPoints(stack);
            if (armor > 0) {
                Component line = colored(getConfig().getString("armor-display.prefix", "&7Armadura: "));
                if (getConfig().getBoolean("armor-display.show-icons", true)) line = line.append(armorIcons(armor));
                if (getConfig().getBoolean("armor-display.show-armor-points", true))
                    line = line.append(colored(" &8(" + (armor == Math.floor(armor) ? Integer.toString((int)armor) : Double.toString(armor)) + ")"));
                parts.add(line);
            }
        }
        if (getConfig().getBoolean("target-info.show-combat-stats", true) && stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            Multimap<Attribute, AttributeModifier> modifiers = meta.getAttributeModifiers();
            if (modifiers != null && !modifiers.isEmpty()) {
                StringJoiner stats = new StringJoiner(", ");
                modifiers.entries().stream().filter(e -> !e.getKey().getKey().getKey().equals("armor")).limit(2).forEach(e -> {
                    String key = e.getKey().getKey().getKey();
                    String name = getConfig().getString("attributes.names." + key, key.replace('_',' '));
                    stats.add(name + " " + String.format(Locale.US, "%+.1f", e.getValue().getAmount()));
                });
                if (stats.length() > 0) parts.add(colored("&c" + stats));
            }
        }
        if (getConfig().getBoolean("target-info.show-amount", true)) parts.add(colored("&7x" + amount));
        return parts;
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("bastionitemdisplay.admin")) { sender.sendMessage(colored("&cSin permiso.")); return true; }
        if (command.getName().equalsIgnoreCase("itemdisplay")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig(); restoreGlow(); clearTeams(); clearLabels(); clearDetails(); startTasks(); send(sender, "reloaded");
            } else sender.sendMessage(colored("&e/itemdisplay reload"));
            return true;
        }
        if (args.length != 2 || !args[0].equalsIgnoreCase("item")) {
            sender.sendMessage(colored("&e/rareza item <comun|pococomun|raro|muyraro|epico|legendario|mitico|divino|reset>")); return true;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage("Solo jugadores."); return true; }
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (stack.getType().isAir()) { send(sender, "no-item"); return true; }
        String choice = args[1].toLowerCase(Locale.ROOT);
        if (!choice.equals("reset") && !RARITIES.contains(choice)) { send(sender, "invalid-rarity"); return true; }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) { send(sender, "no-item"); return true; }
        if (choice.equals("reset")) meta.getPersistentDataContainer().remove(rarityKey);
        else meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, choice);
        stack.setItemMeta(meta);
        if (choice.equals("reset")) send(sender, "rarity-reset");
        else sender.sendMessage(colored(getConfig().getString("messages.prefix", "") + getConfig().getString("messages.rarity-set", "").replace("%rarity%", getConfig().getString("rarities." + choice + ".label", choice))));
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("bastionitemdisplay.admin")) return List.of();
        List<String> options;
        if (command.getName().equalsIgnoreCase("itemdisplay")) options = args.length == 1 ? List.of("reload") : List.of();
        else if (args.length == 1) options = List.of("item");
        else if (args.length == 2 && args[0].equalsIgnoreCase("item")) {
            options = new ArrayList<>(RARITIES); options.add("reset");
        } else options = List.of();
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(s -> s.startsWith(typed)).toList();
    }
}
