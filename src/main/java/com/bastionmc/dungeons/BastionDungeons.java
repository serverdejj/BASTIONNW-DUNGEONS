package com.bastionmc.dungeons;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class BastionDungeons extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, UUID> leaderOf = new HashMap<>();
    private final Map<UUID, UUID> invites = new HashMap<>();
    private final Map<UUID, Location> returns = new HashMap<>();
    private final Map<UUID, String> playingWorld = new HashMap<>();
    private final Set<String> instanceWorlds = new HashSet<>();
    private final Map<String, Set<UUID>> enemies = new HashMap<>();
    private final Map<UUID, String> enemyTypes = new HashMap<>();
    private final Map<String, Set<UUID>> bosses = new HashMap<>();
    private final Map<String, Integer> rounds = new HashMap<>();
    private final Set<String> roundTransitioning = new HashSet<>();
    private final Map<String, Integer> teamSizes = new HashMap<>();
    private final Random random = new Random();
    private final Set<UUID> frozen = new HashSet<>();
    private final Set<UUID> protectedMobs = new HashSet<>();
    private final Map<String, Location> entryPoints = new HashMap<>();
    private final Map<String, String> schematicNames = new HashMap<>();
    private final Map<String, Integer> totalSpawned = new HashMap<>();
    private final Map<String, Integer> defeated = new HashMap<>();
    private final Map<String, Boolean> bossCountdownStarted = new HashMap<>();
    private final Map<UUID, Integer> lostTicks = new HashMap<>();
    private final Map<UUID, Location> lastMobPositions = new HashMap<>();
    private final Map<String, org.bukkit.scheduler.BukkitTask> trackers = new HashMap<>();
    private final Set<UUID> preparing = new HashSet<>();
    private final Set<String> finishing = new HashSet<>();
    private final Set<String> cleanupQueued = new HashSet<>();
    private final Set<String> announcedMissing = new HashSet<>();
    private static final long CLOSED_MS = 30L * 60_000L;
    private static final long OPEN_MS = 5L * 60_000L;
    private static final long CYCLE_MS = CLOSED_MS + OPEN_MS;
    private long cycleStart;
    private long lastAnnouncedWindow = -1L;
    private final Set<UUID> enteredThisWindow = new HashSet<>();
    private final File scheduleFile = new File(getDataFolder(), "schedule.yml");
    // Rondas: 10 por jugador, 12 por jugador, y 1-2 jefes.
    private static final int PASTE_Y = 100;
    private final File spawnFile = new File(getDataFolder(), "spawns.yml");
    private final File mobSpawnFile = new File(getDataFolder(), "mobspawns.yml");
    private final File lootFile = new File(getDataFolder(), "loot.yml");

    @Override public void onEnable() {
        getDataFolder().mkdirs();
        new File(getDataFolder(), "schematics").mkdirs();
        Objects.requireNonNull(getCommand("dungeon")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        loadSchedule();
        Bukkit.getScheduler().runTaskTimer(this, this::checkSchedule, 20L, 20L);
        getLogger().info("Bastion Dungeons 1.2.10-dev listo.");
    }

    private void loadSchedule() {
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(scheduleFile);
        cycleStart = cfg.getLong("cycle-start", System.currentTimeMillis());
        if (cycleStart <= 0 || cycleStart > System.currentTimeMillis()) cycleStart = System.currentTimeMillis();
        // Solo conservar participaciones del periodo de apertura actual.
        long window = currentWindow();
        if (cfg.getLong("entered-window", -1L) == window)
            for (String raw : cfg.getStringList("entered-players")) {
                try { enteredThisWindow.add(UUID.fromString(raw)); } catch (IllegalArgumentException ignored) {}
            }
        saveSchedule();
    }

    private void saveSchedule() {
        var cfg = new org.bukkit.configuration.file.YamlConfiguration();
        cfg.set("cycle-start", cycleStart);
        cfg.set("entered-window", currentWindow());
        cfg.set("entered-players", enteredThisWindow.stream().map(UUID::toString).toList());
        try { cfg.save(scheduleFile); }
        catch (IOException ex) { getLogger().log(java.util.logging.Level.SEVERE, "Error guardando horario de mazmorras", ex); }
    }

    private long currentWindow() { return Math.max(0L, (System.currentTimeMillis() - cycleStart) / CYCLE_MS); }
    private long cycleOffset() { return Math.floorMod(System.currentTimeMillis() - cycleStart, CYCLE_MS); }
    private boolean isOpen() { return cycleOffset() >= CLOSED_MS; }
    private boolean bypass(Player p) { return p.isOp() || p.hasPermission("bastiondungeons.bypass"); }
    private String duration(long ms) {
        long seconds = Math.max(0L, (ms + 999L) / 1000L);
        return (seconds / 60L) + "m " + (seconds % 60L) + "s";
    }
    private boolean canJoin(Player p) {
        if (bypass(p)) return true;
        if (!isOpen()) {
            colored(p, hex("#FFA5A5") + "¡Whoops!" + ChatColor.WHITE + ", ¡Lo siento pero las mazmorras abrirán en " + ChatColor.RED + duration(CLOSED_MS - cycleOffset()) + "." + ChatColor.WHITE + "!");
            return false;
        }
        if (enteredThisWindow.contains(p.getUniqueId())) {
            colored(p, ChatColor.WHITE + "Ya participaste en esta apertura. Próxima oportunidad en " + ChatColor.RED + duration(CYCLE_MS - cycleOffset()) + ChatColor.WHITE + ".");
            return false;
        }
        return true;
    }
    private void checkSchedule() {
        long window = currentWindow();
        if (window != lastAnnouncedWindow) {
            if (lastAnnouncedWindow >= 0) {
                enteredThisWindow.clear();
                saveSchedule();
                for (Player p : Bukkit.getOnlinePlayers())
                    colored(p, hex("#FD9696") + "¡Las mazmorras se han cerrado!" + ChatColor.WHITE + ", ¡Próxima apertura en " + hex("#D2A5FF") + "30 minutos" + ChatColor.WHITE + "!.");
            }
            lastAnnouncedWindow = window;
        }
        // Detectar apertura incluso después de reinicios, sin repetir anuncios cada segundo.
        if (isOpen() && lastAnnouncedOpen != window) {
            lastAnnouncedOpen = window;
            for (Player p : Bukkit.getOnlinePlayers())
                colored(p, hex("#D2A5FF") + "¡Mazmorras abiertas!" + ChatColor.WHITE + ", ¡Tienes " + hex("#D2A5FF") + "5 minutos" + ChatColor.WHITE + " para entrar con /dungeons!");
        }
    }
    private long lastAnnouncedOpen = -1L;

    @Override public void onDisable() { saveSchedule(); }

    private UUID leader(UUID id) { return leaderOf.getOrDefault(id, id); }
    private List<Player> members(UUID leader) {
        List<Player> result = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (leader(p.getUniqueId()).equals(leader)) result.add(p);
        return result;
    }
    private void msg(Player p, String text) { p.sendMessage(ChatColor.GOLD + "[BASTION] " + ChatColor.WHITE + text); }

    private String prefix() {
        String[] colors = {"#9400FF", "#9711FF", "#9B22FF", "#9E32FF", "#A143FF", "#9E32FF", "#9B22FF", "#9711FF", "#9400FF"};
        String name = "MAZMORRAS";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++)
            out.append(net.md_5.bungee.api.ChatColor.of(colors[i])).append(net.md_5.bungee.api.ChatColor.BOLD).append(name.charAt(i));
        return out.append(org.bukkit.ChatColor.DARK_GRAY).append(" » ").toString();
    }

    private void colored(Player p, String message) { p.sendMessage(prefix() + message); }
    private net.md_5.bungee.api.ChatColor hex(String value) { return net.md_5.bungee.api.ChatColor.of(value); }
    private boolean dungeonWorld(World world) { return world != null && instanceWorlds.contains(world.getName()); }
    private boolean dungeonPlayer(Player player) {
        return player != null && dungeonWorld(player.getWorld())
                && player.getWorld().getName().equals(playingWorld.get(player.getUniqueId()));
    }
    private void clearLobbyEffects(Player player) {
        frozen.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.DARKNESS);
    }
    private void beginLobbyEffects(Player player) {
        frozen.add(player.getUniqueId());
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 120, 0, false, false, false));
    }

    // Loot configurable por tipo de enemigo, guardado en YAML con todos los metadatos del ItemStack.
    private String lootMob(String raw) {
        for (String id : List.of("SkeletalMinion", "SkeletalKnight", "SkeletonKing"))
            if (id.equalsIgnoreCase(raw)) return id;
        return null;
    }

    private boolean lootCommand(Player p, String[] args) {
        if (!p.isOp()) { msg(p, "Solo administradores OP."); return true; }
        if (args.length < 3) { msg(p, "Uso: /dungeon loot add <enemigo> <porcentaje> | list <enemigo> | remove <enemigo> <id>"); return true; }
        String mob = lootMob(args[2]);
        if (mob == null) { msg(p, "Enemigos: SkeletalMinion, SkeletalKnight, SkeletonKing."); return true; }
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(lootFile);
        String path = "loot." + mob;
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("list")) {
            var section = cfg.getConfigurationSection(path);
            if (section == null || section.getKeys(false).isEmpty()) { msg(p, "Sin loot configurado para " + mob); return true; }
            for (String key : section.getKeys(false)) {
                org.bukkit.inventory.ItemStack item = cfg.getItemStack(path + "." + key + ".item");
                if (item != null) msg(p, "#" + key + " - " + item.getType() + " x" + item.getAmount() + " - " + cfg.getDouble(path + "." + key + ".chance") + "%");
            }
            return true;
        }
        if (args.length != 4) { msg(p, "Falta porcentaje o ID. Usa /dungeon loot " + action + " " + mob + " <valor>"); return true; }
        if (action.equals("add")) {
            double chance;
            try { chance = Double.parseDouble(args[3]); }
            catch (NumberFormatException ex) { msg(p, "Probabilidad invalida. Ejemplo: 0.01, 5, 100."); return true; }
            if (!Double.isFinite(chance) || chance < 0.01 || chance > 100) { msg(p, "La probabilidad debe estar entre 0.01 y 100."); return true; }
            org.bukkit.inventory.ItemStack item = p.getInventory().getItemInMainHand();
            if (item.getType().isAir()) { msg(p, "Sujeta el objeto en tu mano principal."); return true; }
            String key = UUID.randomUUID().toString().substring(0, 8);
            cfg.set(path + "." + key + ".item", item.clone());
            cfg.set(path + "." + key + ".chance", chance);
            try { cfg.save(lootFile); msg(p, "Loot guardado para " + mob + ": #" + key + " (" + chance + "%)."); }
            catch (IOException ex) { getLogger().log(java.util.logging.Level.SEVERE, "Error guardando loot", ex); msg(p, "No se pudo guardar el loot."); }
            return true;
        }
        if (action.equals("remove")) {
            String key = args[3];
            if (!key.matches("[a-fA-F0-9]{8}") || !cfg.contains(path + "." + key + ".item")) { msg(p, "ID de loot no encontrado. Usa /dungeon loot list " + mob); return true; }
            cfg.set(path + "." + key, null);
            try { cfg.save(lootFile); msg(p, "Loot #" + key + " eliminado."); }
            catch (IOException ex) { getLogger().log(java.util.logging.Level.SEVERE, "Error eliminando loot", ex); msg(p, "No se pudo guardar el cambio."); }
            return true;
        }
        msg(p, "Acciones: add, list, remove.");
        return true;
    }

    private void rollLoot(EntityDeathEvent event, String mob) {
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(lootFile);
        var section = cfg.getConfigurationSection("loot." + mob);
        if (section == null) return;
        java.util.List<org.bukkit.inventory.ItemStack> rewards = new java.util.ArrayList<>();
        for (String key : section.getKeys(false)) {
            String base = "loot." + mob + "." + key;
            org.bukkit.inventory.ItemStack item = cfg.getItemStack(base + ".item");
            double chance = cfg.getDouble(base + ".chance", 0);
            if (item != null && !item.getType().isAir() && chance > 0 && random.nextDouble() * 100 < chance)
                rewards.add(item.clone());
        }
        if (rewards.isEmpty()) return;
        // Soltar directamente al siguiente tick: MythicMobs puede modificar los drops del evento.
        org.bukkit.Location dropAt = event.getEntity().getLocation().clone();
        Bukkit.getScheduler().runTask(this, () -> {
            if (dropAt.getWorld() == null) return;
            for (org.bukkit.inventory.ItemStack reward : rewards)
                dropAt.getWorld().dropItemNaturally(dropAt, reward);
        });
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Solo jugadores."); return true; }
        UUID id = p.getUniqueId();
        if (args.length == 0) { msg(p,"/dungeon party add <jugador> | accept | leave | list | /dungeon go | /dungeon leave | /dungeon setspawn | /dungeon setmobspawn | /dungeon clearmobspawns"); return true; }
        if (args[0].equalsIgnoreCase("loot")) return lootCommand(p, args);
        if (args[0].equalsIgnoreCase("setmobspawn") || args[0].equalsIgnoreCase("clearmobspawns")) {
            if (!p.isOp()) { msg(p, "Solo administradores OP."); return true; }
            String instance = playingWorld.get(id);
            if (instance == null || !instance.equals(p.getWorld().getName()) || !schematicNames.containsKey(instance)) {
                msg(p, "Entra primero a una mazmorra activa."); return true;
            }
            String key = schematicNames.get(instance).replace('.', '_');
            var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(mobSpawnFile);
            String path = "mobspawns." + key;
            if (args[0].equalsIgnoreCase("clearmobspawns")) {
                cfg.set(path, null);
                try { cfg.save(mobSpawnFile); msg(p, "Puntos de enemigos eliminados para este mapa. Usara el spawn aleatorio."); }
                catch (IOException ex) { msg(p, "No se pudieron guardar los cambios."); getLogger().warning(ex.getMessage()); }
                return true;
            }
            Location l = p.getLocation();
            if (!isSafe(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ())) {
                msg(p, "Busca suelo firme con dos bloques libres encima."); return true;
            }
            List<String> points = new ArrayList<>(cfg.getStringList(path));
            String value = l.getX() + "," + (l.getY() - PASTE_Y) + "," + l.getZ();
            if (!points.contains(value)) points.add(value);
            cfg.set(path, points);
            try { cfg.save(mobSpawnFile); msg(p, "Punto de enemigos #" + points.size() + " guardado para " + schematicNames.get(instance) + "."); }
            catch (IOException ex) { msg(p, "No se pudo guardar el punto."); getLogger().warning(ex.getMessage()); }
            return true;
        }
        if (args[0].equalsIgnoreCase("setspawn")) {
            if (!p.isOp()) { msg(p, "Solo administradores OP."); return true; }
            String worldName = playingWorld.get(id);
            if (worldName == null || !worldName.equals(p.getWorld().getName()) || !schematicNames.containsKey(worldName)) {
                msg(p, "Entra primero a una mazmorra activa."); return true;
            }
            Location l = p.getLocation();
            if (!isSafe(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ())) {
                msg(p, "Busca suelo firme con dos bloques libres encima."); return true;
            }
            String key = schematicNames.get(worldName);
            org.bukkit.configuration.file.YamlConfiguration cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(spawnFile);
            String base = "spawns." + key.replace('.', '_');
            cfg.set(base + ".x", l.getX()); cfg.set(base + ".y", l.getY() - PASTE_Y);
            cfg.set(base + ".z", l.getZ()); cfg.set(base + ".yaw", (double) l.getYaw());
            cfg.set(base + ".pitch", (double) l.getPitch());
            try { cfg.save(spawnFile); msg(p, "Spawn guardado para " + key + ". Se usara en las proximas partidas."); }
            catch (IOException ex) { getLogger().log(java.util.logging.Level.SEVERE, "No se pudo guardar spawn", ex); msg(p, "Error guardando spawn."); }
            return true;
        }
        if (args[0].equalsIgnoreCase("party")) {
            if (args.length < 2) { msg(p,"Usa add, accept, leave o list."); return true; }
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "add" -> {
                    if (args.length < 3) { msg(p,"/dungeon party add <jugador>"); return true; }
                    if (!canJoin(p)) return true;
                    if (!leader(id).equals(id)) { msg(p,"Solo el lider invita."); return true; }
                    if (members(id).size() >= 4) { msg(p,"Party llena (maximo 4)."); return true; }
                    Player target = Bukkit.getPlayerExact(args[2]);
                    if (target == null || target.equals(p)) { msg(p,"Jugador no disponible."); return true; }
                    if (!canJoin(target)) { msg(p, "Ese jugador no puede entrar durante esta apertura."); return true; }
                    if (leaderOf.containsKey(target.getUniqueId()) || playingWorld.containsKey(target.getUniqueId()) || preparing.contains(target.getUniqueId())) { msg(p,"Ese jugador ya esta en una party o partida."); return true; }
                    invites.put(target.getUniqueId(), id);
                    msg(target,p.getName()+" te invito. Usa /dungeon party accept"); msg(p,"Invitacion enviada.");
                }
                case "accept" -> {
                    if (!canJoin(p)) return true;
                    UUID owner = invites.remove(id);
                    if (owner == null || Bukkit.getPlayer(owner) == null || playingWorld.containsKey(owner) || preparing.contains(owner) || members(owner).size() >= 4) { msg(p,"No hay invitacion valida."); return true; }
                    if (leaderOf.containsKey(id) || playingWorld.containsKey(id) || preparing.contains(id)) { msg(p,"Ya estas en otra party o partida."); return true; }
                    leaderOf.put(id, owner); msg(p,"Te uniste a la party.");
                    Player lead = Bukkit.getPlayer(owner); if (lead != null) msg(lead,p.getName()+" se unio.");
                }
                case "leave" -> {
                    if (playingWorld.containsKey(id) || preparing.contains(id)) { msg(p,"En partida o preparacion usa /dungeon leave."); return true; }
                    if (leader(id).equals(id)) { leaderOf.entrySet().removeIf(e -> e.getValue().equals(id)); msg(p,"Party disuelta."); }
                    else { leaderOf.remove(id); msg(p,"Saliste de la party."); }
                }
                case "list" -> msg(p,"Party: " + String.join(", ", members(leader(id)).stream().map(Player::getName).toList()));
                default -> msg(p,"Subcomando desconocido.");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("leave")) { leave(p); return true; }
        if (args[0].equalsIgnoreCase("go")) {
            if (!canJoin(p)) return true;
            if (!leader(id).equals(id)) { msg(p,"Solo el lider puede iniciar."); return true; }
            List<Player> team = members(id);
            if (team.stream().anyMatch(q -> !canJoin(q))) { msg(p, "Algún miembro no tiene acceso a esta apertura."); return true; }
            if (team.stream().anyMatch(q -> playingWorld.containsKey(q.getUniqueId()) || preparing.contains(q.getUniqueId()))) { msg(p,"Alguien ya esta en partida."); return true; }
            File folder = new File(getDataFolder(), "schematics");
            File[] schems = folder.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".schem"));
            if (schems == null || schems.length == 0) { msg(p,"Pon archivos .schem en plugins/BastionDungeons/schematics/"); return true; }
            File chosen = schems[new Random().nextInt(schems.length)];
            // Reservar la entrada al iniciar la cuenta atrás para evitar partidas duplicadas.
            for (Player member : team) if (!bypass(member)) enteredThisWindow.add(member.getUniqueId());
            saveSchedule();
            beginLobbyCountdown(team, chosen);
            return true;
        }
        msg(p,"Comando desconocido."); return true;
    }

    private void beginLobbyCountdown(List<Player> team, File file) {
        for (Player member : team) { preparing.add(member.getUniqueId()); beginLobbyEffects(member); }
        for (Player member : team) colored(member,
                net.md_5.bungee.api.ChatColor.of("#D2A5FF") + "¡Prepárate!" + org.bukkit.ChatColor.WHITE
                + ", está empezando la mazmorra " + org.bukkit.ChatColor.DARK_GRAY + "("
                + org.bukkit.ChatColor.RED + "5 Seg" + org.bukkit.ChatColor.DARK_GRAY + ")");
        new org.bukkit.scheduler.BukkitRunnable() {
            int remaining = 5;
            @Override public void run() {
                boolean valid = team.stream().allMatch(p -> p.isOnline() && preparing.contains(p.getUniqueId()));
                if (!valid) {
                    for (Player p : team) {
                        preparing.remove(p.getUniqueId());
                        if (p.isOnline()) clearLobbyEffects(p);
                        if (!playingWorld.containsKey(p.getUniqueId())) enteredThisWindow.remove(p.getUniqueId());
                    }
                    saveSchedule();
                    for (Player p : team) if (p.isOnline()) msg(p, "Preparación cancelada: alguien salió.");
                    cancel(); return;
                }
                if (remaining == 0) {
                    for (Player member : team) colored(member,
                            net.md_5.bungee.api.ChatColor.of("#A5FFB6") + "¡Todo Listo!" + org.bukkit.ChatColor.WHITE
                            + ", Enviándote a la mazmorra");
                    for (Player member : team) {
                        member.sendTitle(ChatColor.WHITE + "☠ " + hex("#9640FC") + ChatColor.BOLD + "INICIANDO MAZMORRA " + ChatColor.RESET + ChatColor.WHITE + "☠",
                                "", 0, 40, 10);
                    }
                    try { start(team, file); }
                    catch (Exception ex) {
                        getLogger().log(java.util.logging.Level.SEVERE, "Error creando dungeon", ex);
                        for (Player p : team) {
                            if (!playingWorld.containsKey(p.getUniqueId())) enteredThisWindow.remove(p.getUniqueId());
                            if (p.isOnline()) clearLobbyEffects(p);
                            if (p.isOnline()) msg(p, "No se pudo iniciar la mazmorra. Revisa consola.");
                        }
                        saveSchedule();
                    } finally { for (Player p : team) preparing.remove(p.getUniqueId()); }
                    cancel(); return;
                }
                for (Player p : team) {
                    p.sendTitle(org.bukkit.ChatColor.LIGHT_PURPLE + "" + remaining,
                            org.bukkit.ChatColor.GRAY + "¡Entrando a la mazmorra!", 0, 23, 0);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.1f);
                }
                remaining--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void start(List<Player> team, File file) throws Exception {
        if (!Bukkit.getPluginManager().isPluginEnabled("MythicMobs"))
            throw new IllegalStateException("MythicMobs no esta habilitado");
        String name = "bd_" + UUID.randomUUID().toString().substring(0, 8);
        World world = new WorldCreator(name).environment(World.Environment.NORMAL)
                .generator(new ChunkGenerator() {}).generateStructures(false).createWorld();
        if (world == null) throw new IllegalStateException("No se pudo crear mundo");
        instanceWorlds.add(name);
        try {
            var format = ClipboardFormats.findByFile(file);
            if (format == null) throw new IOException("Formato schematic desconocido");
            Clipboard clipboard;
            try (ClipboardReader reader = format.getReader(new FileInputStream(file))) { clipboard = reader.read(); }
            BlockVector3 pasteAt = BlockVector3.at(0, 100, 0);
            BlockVector3 min = clipboard.getMinimumPoint().subtract(clipboard.getOrigin()).add(pasteAt);
            BlockVector3 max = clipboard.getMaximumPoint().subtract(clipboard.getOrigin()).add(pasteAt);
            int minCx = min.x() >> 4, maxCx = max.x() >> 4;
            int minCz = min.z() >> 4, maxCz = max.z() >> 4;
            long chunkCount = (long) (maxCx - minCx + 1) * (maxCz - minCz + 1);
            if (chunkCount > 256) throw new IllegalArgumentException("Schematic demasiado grande: " + chunkCount + " chunks (limite 256)");
            // Cargar chunks de manera asincrona antes de ejecutar el pegado.
            // La API de WorldEdit se ejecuta en el hilo del servidor; no se hacen llamadas Bukkit asincronas.
            List<CompletableFuture<Chunk>> loads = new ArrayList<>();
            for (int cx = minCx; cx <= maxCx; cx++) for (int cz = minCz; cz <= maxCz; cz++)
                loads.add(world.getChunkAtAsync(cx, cz, true));
            // Preparación silenciosa: los jugadores ven oscuridad y cuenta regresiva.
            CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).whenComplete((ignored, failure) ->
                Bukkit.getScheduler().runTask(this, () -> {
                    if (!instanceWorlds.contains(name)) return;
                    if (failure != null) {
                        getLogger().log(java.util.logging.Level.SEVERE, "Error cargando chunks", failure);
                        cleanupFailedWorld(name);
                        for (Player member : team) if (member.isOnline()) { clearLobbyEffects(member); msg(member, "No se pudo preparar la mazmorra."); }
                        return;
                    }
                    try {
                        try (EditSession edit = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                            Operations.complete(new ClipboardHolder(clipboard).createPaste(edit)
                                    .to(pasteAt).ignoreAirBlocks(false).build());
                        }
                        Location spawn = configuredSpawn(world, file.getName());
                        if (spawn == null) spawn = safeRandomLocation(world, clipboard, pasteAt);
                        if (spawn == null) throw new IllegalStateException("El schematic no tiene suelo seguro para iniciar");
                        world.setSpawnLocation(spawn);
                        for (Player member : team) {
                            if (!member.isOnline()) continue;
                            returns.put(member.getUniqueId(), member.getLocation().clone());
                            playingWorld.put(member.getUniqueId(), name);
                            member.teleport(spawn);
                            clearLobbyEffects(member);
                            frozen.add(member.getUniqueId());
                        }
                        schematicNames.put(name, file.getName());
                        teamSizes.put(name, team.size());
                        enemies.put(name, new HashSet<>());
                        entryPoints.put(name, spawn.clone());
                        startCountdown(world, clipboard, pasteAt, team.size());
                    } catch (Exception ex) {
                        getLogger().log(java.util.logging.Level.SEVERE, "Error pegando schematic", ex);
                        cleanupFailedWorld(name);
                        for (Player member : team) if (member.isOnline()) { clearLobbyEffects(member); msg(member, "Error creando mazmorra; revisa consola."); }
                    }
                }));
        } catch (Exception ex) {
            cleanupFailedWorld(name);
            throw ex;
        }
    }

    private void cleanupFailedWorld(String name) {
        queueWorldCleanup(name);
    }

    private void leave(Player p) {
        UUID id = p.getUniqueId();
        if (preparing.remove(id)) { clearLobbyEffects(p); msg(p, "Preparación cancelada."); return; }
        String name = playingWorld.get(id);
        if (name != null && finishing.contains(name)) { msg(p, "La partida terminó. Regresarás al lobby en unos segundos."); return; }
        if (name == null) { msg(p, "No estás en una mazmorra."); return; }
        if (playingWorld.values().stream().filter(name::equals).count() == 1) releaseParty(name);
        returnPlayer(p, name);
        if (!playingWorld.containsValue(name)) finishInstance(name);
        msg(p, "Saliste de la mazmorra.");
    }

    private void returnPlayer(Player p, String name) {
        UUID id = p.getUniqueId();
        clearLobbyEffects(p);
        playingWorld.remove(id);
        Location back = returns.remove(id);
        if (p.isOnline()) {
            p.setGameMode(GameMode.SURVIVAL);
            if (back != null) p.teleport(back);
        }
    }

    private void releaseParty(String name) {
        // Eliminar la party completa de los participantes, incluidos sus líderes.
        Set<UUID> participants = new HashSet<>();
        for (var entry : playingWorld.entrySet()) if (name.equals(entry.getValue())) participants.add(entry.getKey());
        Set<UUID> owners = new HashSet<>();
        for (UUID id : participants) owners.add(leader(id));
        for (UUID owner : owners) {
            leaderOf.entrySet().removeIf(e -> e.getKey().equals(owner) || e.getValue().equals(owner));
            invites.entrySet().removeIf(e -> e.getValue().equals(owner));
        }
        for (UUID id : participants) invites.remove(id);
    }

    private void finishInstance(String name) {
        if (!instanceWorlds.contains(name)) return;
        org.bukkit.scheduler.BukkitTask tracker = trackers.remove(name);
        if (tracker != null) tracker.cancel();
        for (UUID mob : enemies.getOrDefault(name, Set.of())) { protectedMobs.remove(mob); lostTicks.remove(mob); lastMobPositions.remove(mob); }
        for (UUID boss : bosses.getOrDefault(name, Set.of())) { protectedMobs.remove(boss); lostTicks.remove(boss); lastMobPositions.remove(boss); }
        bosses.remove(name); rounds.remove(name); roundTransitioning.remove(name);
        enemies.remove(name); bossAreas.remove(name); teamSizes.remove(name);
        entryPoints.remove(name); schematicNames.remove(name);
        totalSpawned.remove(name); defeated.remove(name); bossCountdownStarted.remove(name);
        finishing.remove(name); announcedMissing.remove(name);
        instanceWorlds.remove(name);
        queueWorldCleanup(name);
    }

    private void queueWorldCleanup(String name) {
        if (!cleanupQueued.add(name)) return;
        // Esperar a que todos salgan y el mundo se descargue antes de borrar archivos.
        new org.bukkit.scheduler.BukkitRunnable() {
            int attempts = 0;
            @Override public void run() {
                World world = Bukkit.getWorld(name);
                if (world != null && !world.getPlayers().isEmpty()) {
                    if (++attempts >= 20) { getLogger().warning("No se pudo limpiar " + name + ": jugadores presentes"); cleanupQueued.remove(name); cancel(); }
                    return;
                }
                if (world != null && !Bukkit.unloadWorld(world, false)) {
                    if (++attempts >= 20) { getLogger().warning("No se pudo descargar " + name); cleanupQueued.remove(name); cancel(); }
                    return;
                }
                try {
                    File folder = new File(Bukkit.getWorldContainer(), name);
                    if (folder.isDirectory()) deleteTree(folder.toPath());
                } catch (IOException ex) { getLogger().log(java.util.logging.Level.WARNING, "No se pudo borrar mundo " + name, ex); }
                cleanupQueued.remove(name); cancel();
            }
        }.runTaskTimer(this, 40L, 20L);
    }

    private void deleteTree(java.nio.file.Path root) throws IOException {
        // Solo se llama para mundos temporales bd_... creados por este plugin.
        if (!root.getFileName().toString().matches("bd_[0-9a-f]{8}")) throw new IOException("Nombre de mundo no permitido");
        try (var paths = java.nio.file.Files.walk(root)) {
            for (java.nio.file.Path path : paths.sorted(Comparator.reverseOrder()).toList())
                java.nio.file.Files.deleteIfExists(path);
        }
    }

    @EventHandler public void onDeath(PlayerDeathEvent e) {
        if (playingWorld.containsKey(e.getEntity().getUniqueId())) {
            e.setKeepInventory(false);
            e.setKeepLevel(false);
            // Los objetos caen al suelo para que los compañeros puedan recuperarlos.
            String worldName = playingWorld.get(e.getEntity().getUniqueId());
            Bukkit.getScheduler().runTaskLater(this, () -> {
                World world = Bukkit.getWorld(worldName);
                if (world == null || finishing.contains(worldName) || !instanceWorlds.contains(worldName)) return;
                if (!world.getPlayers().isEmpty() && fighters(world).isEmpty()) {
                    finishing.add(worldName);
                    List<Player> losers = new ArrayList<>(world.getPlayers());
                    releaseParty(worldName);
                    for (Player player : losers) msg(player, "¡DERROTA! Regresando al lobby en 5 segundos...");
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        for (Player player : losers)
                            if (worldName.equals(playingWorld.get(player.getUniqueId()))) returnPlayer(player, worldName);
                        finishInstance(worldName);
                    }, 100L);
                }
            }, 40L);
        }
    }
    @EventHandler public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent e) {
        String name = playingWorld.get(e.getPlayer().getUniqueId());
        if (name != null && Bukkit.getWorld(name) != null) {
            e.setRespawnLocation(Bukkit.getWorld(name).getSpawnLocation());
            Bukkit.getScheduler().runTask(this, () -> e.getPlayer().setGameMode(GameMode.SPECTATOR));
        }
    }
    // MythicMobs API is invoked reflectively to avoid requiring a paid API artifact
    // at build time. Errors are logged rather than spawning vanilla replacements.
    private LivingEntity spawnMythic(String mobId, Location location) throws Exception {
        Class<?> api = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
        Object plugin = api.getMethod("inst").invoke(null);
        Object manager = api.getMethod("getMobManager").invoke(plugin);
        Object active = manager.getClass().getMethod("spawnMob", String.class, Location.class)
                .invoke(manager, mobId, location);
        if (active == null) throw new IllegalStateException("MythicMob no encontrado: " + mobId);
        Object abstractEntity = active.getClass().getMethod("getEntity").invoke(active);
        Entity entity = (Entity) abstractEntity.getClass().getMethod("getBukkitEntity").invoke(abstractEntity);
        if (!(entity instanceof LivingEntity living)) throw new IllegalStateException("No es LivingEntity: " + mobId);
        protectedMobs.add(living.getUniqueId());
        living.setFireTicks(0);
        return living;
    }

    private String gradientBold(String word, String[] colors) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < word.length(); i++)
            result.append(hex(colors[i])).append(ChatColor.BOLD).append(word.charAt(i));
        return result.toString();
    }

    // Nombre y vida en una sola linea sobre la cabeza, sin plugins de modelos/hologramas.
    // Usar la salud real del mob (incluidas las modificaciones de MythicMobs).
    private void updateEnemyName(LivingEntity mob, String mobType) {
        if (mob.isDead() || !instanceWorlds.contains(mob.getWorld().getName())) return;
        String name = switch (mobType) {
            case "SkeletalMinion" -> ChatColor.DARK_GRAY + "☠ " + ChatColor.WHITE + "Esqueleto " + ChatColor.DARK_GRAY + "· "
                    + gradientBold("MALDITO", new String[]{"#C080F6", "#C77CF7", "#CF79F7", "#D675F8", "#CF79F7", "#C77CF7", "#C080F6"});
            case "SkeletalKnight" -> ChatColor.DARK_GRAY + "🗡 " + ChatColor.WHITE + "Guardián " + ChatColor.DARK_GRAY + "· "
                    + gradientBold("ESQUELETO", new String[]{"#F6BE80", "#F7C27D", "#F7C77B", "#F8CB78", "#F8CF75", "#F8CB78", "#F7C77B", "#F7C27D", "#F6BE80"});
            case "SkeletonKing" -> ChatColor.DARK_GRAY + "👑 " + ChatColor.WHITE + "Rey " + ChatColor.DARK_GRAY + "· "
                    + hex("#FF0000") + ChatColor.BOLD + "ESQUELETO";
            default -> "";
        };
        if (name.isEmpty()) return;
        var maxHealth = mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double max = maxHealth == null ? mob.getHealth() : maxHealth.getValue();
        
        long hp = Math.max(0L, (long) Math.ceil(mob.getHealth() + mob.getAbsorptionAmount()));
        long total = Math.max(1L, (long) Math.ceil(max));
        mob.setCustomName(name + ChatColor.GRAY + " | " + ChatColor.RED + "❤ " + hp + "/" + total);
        mob.setCustomNameVisible(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnemyDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob)) return;
        String world = mob.getWorld().getName();
        if (!instanceWorlds.contains(world)) return;
        UUID id = mob.getUniqueId();
        String type = bosses.getOrDefault(world, Set.of()).contains(id) ? "SkeletonKing" :
                enemies.getOrDefault(world, Collections.emptySet()).contains(id) ?
                (enemyTypes.getOrDefault(id, "SkeletalMinion")) : null;
        if (type == null) return;
        // Esperar un tick para leer la vida DESPUES del daño aplicado por Bukkit/MythicMobs.
        Bukkit.getScheduler().runTask(this, () -> {
            if (mob.isValid() && !mob.isDead()) updateEnemyName(mob, type);
        });
    }

    // Las instancias son de solo lectura: proteger la estructura y a los compañeros.
    @EventHandler(ignoreCancelled = true)
    public void onDungeonPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !dungeonPlayer(victim)) return;
        Entity attacker = event.getDamager();
        
        if (attacker instanceof org.bukkit.entity.Projectile projectile) {
            Object shooter = projectile.getShooter();
            if (shooter instanceof Player player && dungeonPlayer(player)) event.setCancelled(true);
        } else if (attacker instanceof Player player && dungeonPlayer(player)) {
            event.setCancelled(true);
        } else if (attacker instanceof org.bukkit.entity.TNTPrimed tnt
                && tnt.getSource() instanceof Player player && dungeonPlayer(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDungeonBreak(BlockBreakEvent event) { if (dungeonWorld(event.getBlock().getWorld())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onDungeonPlace(BlockPlaceEvent event) { if (dungeonWorld(event.getBlock().getWorld())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onDungeonBucketEmpty(PlayerBucketEmptyEvent event) { if (dungeonWorld(event.getPlayer().getWorld())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onDungeonBucketFill(PlayerBucketFillEvent event) { if (dungeonWorld(event.getPlayer().getWorld())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onDungeonIgnite(BlockIgniteEvent event) { if (dungeonWorld(event.getBlock().getWorld())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onDungeonEntityExplode(EntityExplodeEvent event) { if (dungeonWorld(event.getLocation().getWorld())) event.blockList().clear(); }
    @EventHandler(ignoreCancelled = true)
    public void onDungeonBlockExplode(BlockExplodeEvent event) { if (dungeonWorld(event.getBlock().getWorld())) event.blockList().clear(); }
    @EventHandler(ignoreCancelled = true)
    public void onDungeonEntityBlock(EntityChangeBlockEvent event) { if (dungeonWorld(event.getBlock().getWorld())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onDungeonHangingBreak(HangingBreakEvent event) { if (dungeonWorld(event.getEntity().getWorld())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onDungeonHangingPlace(HangingPlaceEvent event) { if (dungeonWorld(event.getEntity().getWorld())) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onDungeonInteract(PlayerInteractEvent event) {
        if (!dungeonWorld(event.getPlayer().getWorld())) return;
        if (event.getClickedBlock() != null && (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) {
            // Bloquear cambios de estructura; permitir puertas y botones para mapas interactivos.
            org.bukkit.Material material = event.getClickedBlock().getType();
            if (material == Material.FARMLAND || material == Material.CAMPFIRE || material == Material.SOUL_CAMPFIRE
                    || material == Material.RESPAWN_ANCHOR || material == Material.COMPOSTER
                    || material == Material.CAULDRON || material == Material.WATER_CAULDRON
                    || material == Material.LAVA_CAULDRON || material == Material.POWDER_SNOW_CAULDRON
                    || material.name().contains("CANDLE") || material.name().contains("BED")) event.setCancelled(true);
        }
        if (event.getAction() == org.bukkit.event.block.Action.PHYSICAL) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onDungeonArmorStand(PlayerArmorStandManipulateEvent event) { if (dungeonWorld(event.getPlayer().getWorld())) event.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (protectedMobs.contains(event.getEntity().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        Player player = event.getPlayer();
        String instance = playingWorld.get(player.getUniqueId());
        if (instance != null && instance.equals(player.getWorld().getName())
                && player.getGameMode() != GameMode.SPECTATOR) {
            Location center = entryPoints.get(instance);
            Location destination = event.getTo();
            if (center != null && destination.getWorld().equals(center.getWorld())) {
                double dx = destination.getX() - center.getX();
                double dz = destination.getZ() - center.getZ();
                if (dx * dx + dz * dz > 33.0 * 33.0) {
                    // Programar el teleport fuera de PlayerMoveEvent para evitar reentradas.
                    Bukkit.getScheduler().runTask(this, () -> {
                        if (instance.equals(playingWorld.get(player.getUniqueId())) && player.isOnline()) {
                            player.teleport(center.clone());
                            msg(player, "No puedes alejarte más de 33 bloques del inicio de la mazmorra.");
                        }
                    });
                    event.setTo(event.getFrom());
                    return;
                }
            }
        }
        if (!frozen.contains(player.getUniqueId())) return;
        Location from = event.getFrom(), to = event.getTo();
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location locked = from.clone();
            locked.setYaw(to.getYaw()); locked.setPitch(to.getPitch());
            event.setTo(locked);
        }
    }

    private void startCountdown(World world, Clipboard clipboard, BlockVector3 pasteAt, int partySize) {
        String name = world.getName();
        new org.bukkit.scheduler.BukkitRunnable() {
            int remaining = 5;
            @Override public void run() {
                if (!instanceWorlds.contains(name) || world.getPlayers().isEmpty()) { cancel(); return; }
                if (remaining == 0) {
                    for (Player player : world.getPlayers()) {
                        frozen.remove(player.getUniqueId());
                        player.sendTitle(ChatColor.GREEN + "¡A LUCHAR!", ChatColor.YELLOW + "¡Derrota a los enemigos!", 0, 35, 10);
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.65f, 1.5f);
                    }
                    bossAreas.put(name, new BossArea(clipboard, pasteAt));
                    totalSpawned.put(name, 0); defeated.put(name, 0); rounds.put(name, 1);
                    spawnNextMobs(world);
                    startTracker(world);
                    cancel(); return;
                }
                for (Player player : world.getPlayers()) {
                    player.sendTitle(ChatColor.GOLD + "" + remaining, ChatColor.YELLOW + "¡PREPÁRATE!", 0, 23, 0);
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, remaining <= 3 ? 1.7f : 1.1f);
                }
                remaining--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private Location nearbySafeLocation(World world, Location center, int minRadius, int maxRadius) {
        if (center == null) return null;
        for (int i = 0; i < 120; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int radius = minRadius + random.nextInt(maxRadius - minRadius + 1);
            int x = center.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = center.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            for (int dy = 3; dy >= -6; dy--) {
                int y = center.getBlockY() + dy;
                if (y <= world.getMinHeight() + 1 || y >= world.getMaxHeight() - 2) continue;
                var floor = world.getBlockAt(x, y - 1, z);
                var feet = world.getBlockAt(x, y, z);
                var head = world.getBlockAt(x, y + 1, z);
                if (!floor.getType().isSolid() || !feet.isPassable() || !head.isPassable()) continue;
                if (floor.isLiquid() || feet.isLiquid() || head.isLiquid()) continue;
                String type = floor.getType().name();
                if (type.contains("LEAVES") || type.contains("MAGMA") || type.contains("CACTUS")) continue;
                Location loc = new Location(world, x + .5, y, z + .5);
                if (world.getNearbyEntities(loc, 2, 2, 2).stream().anyMatch(e -> e instanceof Player)) continue;
                return loc;
            }
        }
        return null;
    }

    private Location safeRandomLocation(World world, Clipboard clipboard, BlockVector3 pasteAt) {
        BlockVector3 min = clipboard.getMinimumPoint().subtract(clipboard.getOrigin()).add(pasteAt);
        BlockVector3 max = clipboard.getMaximumPoint().subtract(clipboard.getOrigin()).add(pasteAt);
        int xMin = min.x(), xMax = max.x();
        int yMin = Math.max(world.getMinHeight() + 2, min.y());
        int yMax = Math.min(world.getMaxHeight() - 3, max.y());
        int zMin = min.z(), zMax = max.z();
        if (xMin > xMax || yMin > yMax || zMin > zMax) return null;
        // Elegir columnas X/Z y recorrer sus alturas: evita la baja probabilidad
        // de acertar con una superficie al elegir Y completamente al azar.
        for (int i = 0; i < 300; i++) {
            int x = xMin + random.nextInt(xMax - xMin + 1);
            int z = zMin + random.nextInt(zMax - zMin + 1);
            List<Integer> candidates = new ArrayList<>();
            for (int y = yMin; y <= yMax; y++) {
                var floor = world.getBlockAt(x, y - 1, z);
                var feet = world.getBlockAt(x, y, z);
                var head = world.getBlockAt(x, y + 1, z);
                if (!floor.getType().isSolid() || !feet.isPassable() || !head.isPassable()) continue;
                if (floor.isLiquid() || feet.isLiquid() || head.isLiquid()) continue;
                String floorName = floor.getType().name();
                if (floorName.contains("LEAVES") || floorName.contains("MAGMA") || floorName.contains("CACTUS")) continue;
                candidates.add(y);
            }
            if (candidates.isEmpty()) continue;
            int y = candidates.get(random.nextInt(candidates.size()));
            Location loc = new Location(world, x + .5, y, z + .5);
            if (world.getNearbyEntities(loc, 3, 3, 3).stream().noneMatch(e -> e instanceof Player)) return loc;
        }
        return null;
    }

    private boolean isSafe(World world, int x, int y, int z) {
        if (world == null || y <= world.getMinHeight() + 1 || y >= world.getMaxHeight() - 2) return false;
        var floor = world.getBlockAt(x, y - 1, z);
        var feet = world.getBlockAt(x, y, z);
        var head = world.getBlockAt(x, y + 1, z);
        if (!floor.getType().isSolid() || !feet.isPassable() || !head.isPassable()) return false;
        if (floor.isLiquid() || feet.isLiquid() || head.isLiquid()) return false;
        String type = floor.getType().name();
        return !type.contains("LEAVES") && !type.contains("MAGMA") && !type.contains("CACTUS");
    }

    private Location configuredSpawn(World world, String schematic) {
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(spawnFile);
        String base = "spawns." + schematic.replace('.', '_');
        if (!cfg.contains(base + ".x")) return null;
        Location loc = new Location(world, cfg.getDouble(base + ".x"),
                cfg.getDouble(base + ".y") + PASTE_Y, cfg.getDouble(base + ".z"),
                (float) cfg.getDouble(base + ".yaw"), (float) cfg.getDouble(base + ".pitch"));
        if (!isSafe(world, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
            getLogger().warning("Spawn guardado inseguro para " + schematic + "; usando spawn automatico.");
            return null;
        }
        return loc;
    }

    private record BossArea(Clipboard clipboard, BlockVector3 pasteAt) {}
    private final Map<String, BossArea> bossAreas = new HashMap<>();

    private List<Player> fighters(World world) {
        return world.getPlayers().stream().filter(p -> p.getGameMode() != GameMode.SPECTATOR).toList();
    }

    // Si el schematic tiene puntos configurados, usarlos; si no, conservar el spawn original.
    private Location enemySpawnLocation(World world, Location near) {
        String schematic = schematicNames.get(world.getName());
        if (schematic == null) return nearbySafeLocation(world, near, 5, 8);
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(mobSpawnFile);
        List<String> points = cfg.getStringList("mobspawns." + schematic.replace('.', '_'));
        if (points.isEmpty()) return nearbySafeLocation(world, near, 5, 8);
        List<Location> safe = new ArrayList<>();
        for (String raw : points) {
            try {
                String[] xyz = raw.split(",");
                Location loc = new Location(world, Double.parseDouble(xyz[0]), Double.parseDouble(xyz[1]) + PASTE_Y, Double.parseDouble(xyz[2]));
                if (isSafe(world, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) safe.add(loc);
            } catch (RuntimeException ex) { getLogger().warning("Punto de mob invalido: " + raw); }
        }
        return safe.isEmpty() ? null : safe.get(random.nextInt(safe.size()));
    }

    private void setEnemyHealth(LivingEntity mob, double hp) {
        var attribute = mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attribute != null) {
            // El limite vanilla de MAX_HEALTH es 1024; el resto del jefe se maneja con absorcion.
            double base = Math.min(hp, 1024.0);
            attribute.setBaseValue(base);
            mob.setHealth(base);
            if (hp > base) mob.setAbsorptionAmount(hp - base);
        }
    }

    private int roundTotal(String name) {
        int players = Math.max(1, teamSizes.getOrDefault(name, 1));
        return (rounds.getOrDefault(name, 1) == 1 ? 10 : 12) * players;
    }

    private int batchSize(String name) {
        int players = Math.max(1, teamSizes.getOrDefault(name, 1));
        int round = rounds.getOrDefault(name, 1);
        int spawned = totalSpawned.getOrDefault(name, 0);
        if (round == 1) return (spawned < 6 * players ? 3 : 4) * players;
        return 4 * players;
    }

    private void spawnNextMobs(World world) {
        String name = world.getName();
        if (!instanceWorlds.contains(name) || finishing.contains(name) || roundTransitioning.contains(name)
                || rounds.getOrDefault(name, 1) >= 3) return;
        Set<UUID> active = enemies.get(name);
        if (active == null || !active.isEmpty()) return; // Oleadas completas, nunca mezclar grupos.
        List<Player> players = fighters(world);
        if (players.isEmpty()) return;
        int total = roundTotal(name);
        int count = Math.min(batchSize(name), total - totalSpawned.getOrDefault(name, 0));
        int attempts = 0;
        while (active.size() < count && attempts++ < Math.max(25, count * 12)) {
            Player target = players.get(random.nextInt(players.size()));
            Location loc = enemySpawnLocation(world, target.getLocation());
            if (loc == null) continue;
            try {
                String mobId = random.nextBoolean() ? "SkeletalMinion" : "SkeletalKnight";
                LivingEntity mob = spawnMythic(mobId, loc);
                setEnemyHealth(mob, mobId.equals("SkeletalMinion") ? 50 : 125);
                active.add(mob.getUniqueId());
                enemyTypes.put(mob.getUniqueId(), mobId);
                updateEnemyName(mob, mobId);
                totalSpawned.merge(name, 1, Integer::sum);
            } catch (Exception ex) { getLogger().log(java.util.logging.Level.WARNING, "Error invocando enemigo", ex); break; }
        }
        if (active.isEmpty() && totalSpawned.getOrDefault(name, 0) < total && announcedMissing.add(name)) {
            getLogger().warning("Sin posiciones seguras para enemigos en " + name);
            for (Player p : players) msg(p, "No se encontraron posiciones seguras para enemigos.");
        }
        if (!active.isEmpty()) announcedMissing.remove(name);
    }

    private void advanceRound(World world) {
        String name = world.getName();
        if (!instanceWorlds.contains(name) || finishing.contains(name) || !roundTransitioning.add(name)) return;
        int next = rounds.getOrDefault(name, 1) + 1;
        if (next > 3) return;
        String title = next == 2 ? hex("#9640FC") + "" + ChatColor.BOLD + "RONDA 2"
                : hex("#FF3333") + "" + ChatColor.BOLD + "¡JEFE FINAL!";
        for (Player p : fighters(world)) {
            p.sendTitle(ChatColor.WHITE + "☠ " + title + ChatColor.WHITE + " ☠", "", 5, 90, 5);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.3f);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!instanceWorlds.contains(name) || finishing.contains(name) || fighters(world).isEmpty()) return;
            rounds.put(name, next);
            totalSpawned.put(name, 0);
            defeated.put(name, 0);
            roundTransitioning.remove(name);
            if (next == 2) spawnNextMobs(world);
            else spawnBoss(world);
        }, 100L);
    }

    // Persecución continua; si un mob no avanza durante 4 segundos, reaparece cerca del objetivo.
    private void pursueEnemy(LivingEntity mob, UUID mobId, List<Player> players, World world) {
        Player closest = players.stream()
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(mob.getLocation())))
                .orElse(null);
        if (closest == null) return;
        if (mob instanceof org.bukkit.entity.Mob pathfindingMob) pathfindingMob.setTarget(closest);
        Location previous = lastMobPositions.put(mobId, mob.getLocation().clone());
        double distance = mob.getLocation().distanceSquared(closest.getLocation());
        if (distance < 3.5 * 3.5) { lostTicks.remove(mobId); return; }
        boolean stuck = previous != null && previous.distanceSquared(mob.getLocation()) < 0.35 * 0.35;
        // Un mob que queda lejos del área también debe regresar al combate.
        Location spawn = entryPoints.get(world.getName());
        boolean escaped = spawn != null && horizontalDistanceSquared(spawn, mob.getLocation()) > 33.0 * 33.0;
        int stalled = stuck || escaped ? lostTicks.merge(mobId, 1, Integer::sum) : 0;
        if (!stuck && !escaped) lostTicks.remove(mobId);
        if (escaped || stalled >= 4) {
            Location destination = nearbySafeLocation(world, closest.getLocation(), 2, 4);
            if (destination != null && (spawn == null || horizontalDistanceSquared(spawn, destination) <= 33.0 * 33.0)) {
                mob.teleport(destination);
                if (mob instanceof org.bukkit.entity.Mob pathfindingMob) pathfindingMob.setTarget(closest);
                lastMobPositions.put(mobId, destination.clone());
                lostTicks.remove(mobId);
            }
        }
    }

    private double horizontalDistanceSquared(Location a, Location b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private void startTracker(World world) {
        String name = world.getName();
        trackers.put(name, Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!instanceWorlds.contains(name) || finishing.contains(name)) return;
            Set<UUID> active = enemies.get(name);
            if (active == null) return;
            List<Player> players = fighters(world);
            if (players.isEmpty()) return;
            for (UUID mobId : new ArrayList<>(active)) {
                Entity entity = Bukkit.getEntity(mobId);
                if (!(entity instanceof LivingEntity mob) || mob.isDead()) {
                    active.remove(mobId); protectedMobs.remove(mobId);
                    lostTicks.remove(mobId); lastMobPositions.remove(mobId); enemyTypes.remove(mobId);
                    defeated.merge(name, 1, Integer::sum);
                    continue;
                }
                updateEnemyName(mob, enemyTypes.getOrDefault(mobId, "SkeletalMinion"));
                pursueEnemy(mob, mobId, players, world);
            }
            for (UUID bossId : new HashSet<>(bosses.getOrDefault(name, Set.of()))) {
                Entity entity = Bukkit.getEntity(bossId);
                if (entity instanceof LivingEntity boss && !boss.isDead()) {
                    updateEnemyName(boss, "SkeletonKing");
                    pursueEnemy(boss, bossId, players, world);
                }
            }
            if (rounds.getOrDefault(name, 1) < 3 && !roundTransitioning.contains(name)) {
                if (defeated.getOrDefault(name, 0) >= roundTotal(name) && active.isEmpty()) advanceRound(world);
                else spawnNextMobs(world);
            }
        }, 40L, 20L));
    }

    private void spawnBoss(World world) {
        String name = world.getName();
        List<Player> players = fighters(world);
        if (players.isEmpty()) return;
        int count = teamSizes.getOrDefault(name, 1) >= 3 ? 2 : 1;
        Set<UUID> spawnedBosses = bosses.computeIfAbsent(name, k -> new HashSet<>());
        for (int i = 0; i < count; i++) {
            Location loc = enemySpawnLocation(world, players.get(i % players.size()).getLocation());
            if (loc == null) { getLogger().warning("No hay lugar para SkeletonKing en " + name); continue; }
            try {
                LivingEntity boss = spawnMythic("SkeletonKing", loc);
                setEnemyHealth(boss, 500);
                spawnedBosses.add(boss.getUniqueId());
                updateEnemyName(boss, "SkeletonKing");
            } catch (Exception ex) { getLogger().log(java.util.logging.Level.WARNING, "Error al crear SkeletonKing", ex); }
        }
        if (spawnedBosses.isEmpty()) {
            for (Player p : players) msg(p, "No se pudo invocar al jefe. Revisa consola.");
        } else {
            for (Player p : world.getPlayers()) p.sendMessage(hex("#FF0000") + "" + ChatColor.BOLD + "JEFE ESQUELETO " + ChatColor.DARK_GRAY + "» " + ChatColor.WHITE + "¡Ha aparecido el Jefe Final!");
        }
    }

    @EventHandler public void onMobDeath(EntityDeathEvent event) {
        String name = event.getEntity().getWorld().getName();
        UUID dead = event.getEntity().getUniqueId();
        String deadType = enemyTypes.get(dead);
        if (deadType == null && bosses.getOrDefault(name, Set.of()).contains(dead)) deadType = "SkeletonKing";
        if (deadType != null) rollLoot(event, deadType);
        protectedMobs.remove(dead); lostTicks.remove(dead); lastMobPositions.remove(dead); enemyTypes.remove(dead);
        Set<UUID> active = enemies.get(name);
        if (active == null) return;
        if (active.remove(dead)) {
            int kills = defeated.merge(name, 1, Integer::sum);
            for (Player p : event.getEntity().getWorld().getPlayers())
                p.sendMessage(net.md_5.bungee.api.ChatColor.of("#FFA5A5") + "¡Uno menos!" + org.bukkit.ChatColor.WHITE
                        + ", ¡Quedan " + Math.max(0, roundTotal(name) - kills) + "/" + roundTotal(name) + " enemigos!.");
            if (kills >= roundTotal(name) && active.isEmpty()) advanceRound(event.getEntity().getWorld());
            else spawnNextMobs(event.getEntity().getWorld());
        } else if (bosses.getOrDefault(name, Set.of()).contains(dead)) {
            Set<UUID> remainingBosses = bosses.get(name);
            if (remainingBosses != null) remainingBosses.remove(dead);
            if (remainingBosses != null && !remainingBosses.isEmpty()) {
                for (Player p : event.getEntity().getWorld().getPlayers())
                    msg(p, "¡Un jefe derrotado! Quedan " + remainingBosses.size() + ".");
                return;
            }
            bosses.remove(name);
            if (!finishing.add(name)) return;
            World world = event.getEntity().getWorld();
            List<Player> winners = new ArrayList<>(world.getPlayers());
            releaseParty(name);
            for (Player p : winners) {
                frozen.remove(p.getUniqueId());
                p.sendTitle(net.md_5.bungee.api.ChatColor.of("#FFF800") + "" + ChatColor.BOLD + "¡VICTORIA!",
                        ChatColor.WHITE + "¡Han derrotado a todos los enemigos!", 10, 80, 10);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                msg(p, "¡Victoria! Tienes 10 segundos para explorar y recoger recompensas.");
            }
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (Player p : winners) {
                    if (name.equals(playingWorld.get(p.getUniqueId()))) returnPlayer(p, name);
                }
                finishInstance(name);
            }, 200L);
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        clearLobbyEffects(e.getPlayer());
        // Limpiar la sesion al desconectar, para que no quede bloqueado "ya esta en partida".
        String name = playingWorld.get(e.getPlayer().getUniqueId());
        if (name != null && finishing.contains(name)) returnPlayer(e.getPlayer(), name);
        else if (name != null || preparing.contains(e.getPlayer().getUniqueId())) leave(e.getPlayer());
    }
}
