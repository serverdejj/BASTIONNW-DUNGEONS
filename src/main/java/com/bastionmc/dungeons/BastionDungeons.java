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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.block.Block;
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
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;
import org.joml.Vector3f;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class BastionDungeons extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private enum Difficulty { EASY, NORMAL, HARD }
    private static final String VOTE_TITLE = "§5⚔ Votación de dificultad";
    private final Map<UUID, VoteSession> voteSessions = new HashMap<>();
    private final Map<UUID, VoteSession> voterSessions = new HashMap<>();
    private final Map<UUID, Difficulty> pendingDifficulties = new HashMap<>();
    private final Map<String, Difficulty> worldDifficulties = new HashMap<>();
    private final Map<String, Integer> stalkTicks = new HashMap<>();
    private static final class VoteSession {
        final UUID leader;
        final List<UUID> members;
        final File schematic;
        final Map<UUID, Difficulty> votes = new HashMap<>();
        int remaining = -1;
        int votingSeconds = 0;
        boolean results = false;
        org.bukkit.scheduler.BukkitTask task;
        VoteSession(UUID leader, List<Player> players, File schematic) {
            this.leader = leader;
            this.members = players.stream().map(Player::getUniqueId).toList();
            this.schematic = schematic;
        }
    }
    private final Map<UUID, UUID> leaderOf = new HashMap<>();
    private final Map<UUID, UUID> invites = new HashMap<>();
    private final Map<UUID, Location> returns = new HashMap<>();
    private final Map<UUID, String> playingWorld = new HashMap<>();
    private final Set<UUID> awaitingDungeonRespawn = new HashSet<>();
    private final Set<String> instanceWorlds = new HashSet<>();
    private int mythicSpawnDepth = 0;
    // Progresion de aventura: estado por instancia, configuracion persistente por schematic.
    private final File adventureFile = new File(getDataFolder(), "adventure.yml");
    private record Encounter(Location center, double radius) {}
    private final Map<String, Integer> adventureCompletedZones = new HashMap<>();
    private final Map<String, Integer> adventureActiveZone = new HashMap<>();
    private final Map<String, Integer> adventureWave = new HashMap<>();
    private final Map<UUID, Long> adventureOutsideSince = new HashMap<>();
    private final Set<String> adventureNextWavePending = new HashSet<>();
    private final Set<UUID> parkourReturning = new HashSet<>();
    private final Set<String> adventureBossStarted = new HashSet<>();
    private final Map<String, Location> adventureBossCenters = new HashMap<>();
    private final Set<String> adventureReady = new HashSet<>();
    private final Map<String, Set<UUID>> enemies = new HashMap<>();
    private final Map<UUID, String> enemyTypes = new HashMap<>();
    private final Map<String, Set<UUID>> bosses = new HashMap<>();
    private final Map<String, Integer> rounds = new HashMap<>();
    private final Set<String> roundTransitioning = new HashSet<>();
    private final Map<String, Integer> teamSizes = new HashMap<>();
    private final Random random = new Random();
    private final Set<UUID> frozen = new HashSet<>();
    // Efectos optativos del Rey Esqueleto: se aplican solamente a los jefes MythicMobs
    // controlados por BastionDungeons, nunca a criaturas normales del servidor.
    private final Set<UUID> kingLanding = new HashSet<>();
    private final Map<UUID, Long> kingLastLaunch = new HashMap<>();
    // Hologramas independientes de la entidad (compatibles con disfraces de jugador).
    private final Map<UUID, List<TextDisplay>> mobHolograms = new HashMap<>();
    private final Set<UUID> slimeBossJumping = new HashSet<>();
    private final Map<UUID, org.bukkit.scheduler.BukkitTask> slimeBossTasks = new HashMap<>();
    // Dragon de Netherybreis: rutas y tareas por instancia, nunca globales.
    private final Map<String, org.bukkit.scheduler.BukkitTask> netherDragonTasks = new HashMap<>();
    private final Map<String, org.bukkit.scheduler.BukkitTask> netherCountdownTasks = new HashMap<>();
    // Una BossBar y una secuencia de aterrizaje por instancia Nether.
    private final Map<String, org.bukkit.boss.BossBar> netherDragonBars = new HashMap<>();
    private final Map<String, org.bukkit.scheduler.BukkitTask> netherDragonBarTasks = new HashMap<>();
    private final Map<String, org.bukkit.scheduler.BukkitTask> netherDragonEntranceTasks = new HashMap<>();
    private final Map<String, org.bukkit.scheduler.BukkitTask> netherDragonDeathTasks = new HashMap<>();
    private final Map<String, UUID> netherDragonDeathEntities = new HashMap<>();
    private final Map<UUID, UUID> dragonFireballOwners = new HashMap<>();

    // Herramientas temporales, personales y exclusivas para OP.
    private final Set<UUID> adminBreakMode = new HashSet<>();
    private final Set<UUID> adminFreeMode = new HashSet<>();
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
    private org.bukkit.configuration.file.FileConfiguration menusConfig;
    private org.bukkit.configuration.file.FileConfiguration messagesConfig;
    private org.bukkit.configuration.file.FileConfiguration difficultiesConfig;
    private org.bukkit.configuration.file.FileConfiguration soundsConfig;
    private void loadExtraConfigs() {
        for (String file : List.of("menus.yml", "messages.yml", "difficulties.yml", "sounds.yml")) saveResourceIfAbsent(file);
        menusConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new File(getDataFolder(), "menus.yml"));
        messagesConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        difficultiesConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new File(getDataFolder(), "difficulties.yml"));
        soundsConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new File(getDataFolder(), "sounds.yml"));
    }
    private void saveResourceIfAbsent(String name) {
        if (!new File(getDataFolder(), name).exists()) saveResource(name, false);
    }
    private String format(String value) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("&#([0-9a-fA-F]{6})").matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(hex("#" + matcher.group(1)).toString()));
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
    private String menuText(String key, String fallback) { return format(menusConfig.getString(key, fallback)); }
    private String messageText(String key, String fallback) { return format(messagesConfig.getString(key, fallback)); }
    private String voteTitle() { return menuText("voting.title", "&5⚔ Votación de dificultad"); }
    private String previewTitle() { return menuText("preview.title", "&5⚔ BASTION DUNGEONS"); }
    private int voteSlot(Difficulty d) { return menusConfig.getInt("voting.options." + d.name().toLowerCase(Locale.ROOT) + ".slot", d == Difficulty.EASY ? 11 : d == Difficulty.NORMAL ? 13 : 15); }
    private String difficultyName(Difficulty d) { return menuText("voting.options." + d.name().toLowerCase(Locale.ROOT) + ".name", d.name()); }
    private String replaceVoteVars(String line, VoteSession session, Difficulty d, Player viewer) {
        long count = session.votes.values().stream().filter(v -> v == d).count();
        String names = session.members.stream().filter(id -> session.votes.get(id) == d)
                .map(id -> Bukkit.getPlayer(id) == null ? id.toString() : Bukkit.getPlayer(id).getName())
                .collect(java.util.stream.Collectors.joining(", "));
        return format(line.replace("{votes}", "" + count).replace("{voters}", names.isEmpty() ? "-" : names)
                .replace("{players}", "" + session.members.size()).replace("{voted}", "" + session.votes.size())
                .replace("{seconds}", "" + Math.max(0, session.results ? session.remaining : session.votingSeconds))
                .replace("{your_vote}", session.votes.get(viewer.getUniqueId()) == d ? "✔" : ""));
    }
    private double difficultyMultiplier(Difficulty d, String key, double fallback) {
        String path = "difficulty." + d.name().toLowerCase(Locale.ROOT) + "." + key;
        // Vida y dano editables en config.yml, sin requerir modificar difficulties.yml.
        if ((key.equals("health-multiplier") || key.equals("damage-multiplier")) && getConfig().contains(path))
            return getConfig().getDouble(path, fallback);
        return difficultiesConfig.getDouble(path, fallback);
    }

    @Override public void onEnable() {
        getDataFolder().mkdirs();
        new File(getDataFolder(), "schematics").mkdirs();
        Objects.requireNonNull(getCommand("dungeon")).setExecutor(this);
        Objects.requireNonNull(getCommand("dungeon")).setTabCompleter(this);
        saveDefaultConfig();
        loadExtraConfigs();
        getServer().getPluginManager().registerEvents(this, this);
        loadSchedule();
        Bukkit.getScheduler().runTaskTimer(this, this::checkSchedule, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, this::checkMapVoidPlayers, 5L, 5L);
        Bukkit.getScheduler().runTaskTimer(this, this::tickMobHolograms, 1L, 1L);
        getLogger().info("Bastion Dungeons 1.2.26-dev listo.");
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

    @Override public void onDisable() {
        for (UUID id : new HashSet<>(mobHolograms.keySet())) removeMobHologram(id);
        for (org.bukkit.scheduler.BukkitTask task : slimeBossTasks.values()) task.cancel();
        slimeBossTasks.clear(); slimeBossJumping.clear();
        netherDragonTasks.values().forEach(org.bukkit.scheduler.BukkitTask::cancel);
        netherCountdownTasks.values().forEach(org.bukkit.scheduler.BukkitTask::cancel);
        netherDragonTasks.clear(); netherCountdownTasks.clear();
        netherDragonDeathTasks.values().forEach(org.bukkit.scheduler.BukkitTask::cancel);
        netherDragonDeathTasks.clear();
        for (UUID visual : netherDragonDeathEntities.values()) {
            Entity entity = Bukkit.getEntity(visual);
            if (entity != null) entity.remove();
        }
        netherDragonDeathEntities.clear();
        netherDragonEntranceTasks.values().forEach(org.bukkit.scheduler.BukkitTask::cancel);
        netherDragonBarTasks.values().forEach(org.bukkit.scheduler.BukkitTask::cancel);
        netherDragonBars.values().forEach(org.bukkit.boss.BossBar::removeAll);
        netherDragonEntranceTasks.clear(); netherDragonBarTasks.clear(); netherDragonBars.clear();
        kingLanding.clear(); kingLastLaunch.clear(); saveSchedule();
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("party", "go", "leave"));
            if (player.isOp()) options.addAll(List.of("map", "loot", "setspawn", "setmobspawn", "clearmobspawns", "romper", "libre", "mobs", "radio", "boss", "dragon", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("party")) options.addAll(List.of("add", "accept", "leave", "list"));
        else if (args.length == 3 && args[0].equalsIgnoreCase("party") && args[1].equalsIgnoreCase("add"))
            options.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        else if (player.isOp() && args[0].equalsIgnoreCase("go") && args.length == 2) {
            options.add("map");
            for (File f : availableMaps()) options.add(mapId(f));
        }
        else if (player.isOp() && args[0].equalsIgnoreCase("map") && args.length == 2) {
            for (File f : availableMaps()) options.add(mapId(f));
        }
        else if (player.isOp() && args[0].equalsIgnoreCase("map") && args.length == 3) options.addAll(List.of("facil", "normal", "dificil"));
        else if (player.isOp() && args[0].equalsIgnoreCase("go") && args.length == 4 && args[1].equalsIgnoreCase("map")) options.addAll(List.of("facil", "normal", "dificil"));
        else if (player.isOp() && args.length == 2 && args[0].equalsIgnoreCase("setspawn")) options.add("parkour");
        else if (player.isOp() && args[0].equalsIgnoreCase("go") && args.length == 3 && args[1].equalsIgnoreCase("map")) {
            for (File f : availableMaps()) options.add(mapId(f));
        }
        else if (player.isOp() && args.length == 2 && args[0].equalsIgnoreCase("radio")) options.addAll(List.of("10", "15", "20"));
        else if (player.isOp() && args.length == 3 && args[0].equalsIgnoreCase("radio")) options.add("oleadas");
        else if (player.isOp() && args.length == 4 && args[0].equalsIgnoreCase("radio")) options.addAll(List.of("0", "1", "2", "3"));
        else if (player.isOp() && args.length == 2 && args[0].equalsIgnoreCase("mobs")) options.addAll(List.of("radio", "list", "clear"));
        else if (player.isOp() && args.length == 3 && args[0].equalsIgnoreCase("mobs") && args[1].equalsIgnoreCase("radio")) options.addAll(List.of("5", "10", "15", "20"));
        else if (player.isOp() && args.length == 2 && args[0].equalsIgnoreCase("dragon")) options.addAll(List.of("spawn", "punto", "test", "info"));
        else if (player.isOp() && args.length == 3 && args[0].equalsIgnoreCase("dragon") && args[1].equalsIgnoreCase("punto")) options.addAll(List.of("add", "list", "remove", "clear"));
        else if (player.isOp() && args.length == 4 && args[0].equalsIgnoreCase("dragon") && args[1].equalsIgnoreCase("punto") && args[2].equalsIgnoreCase("remove")) options.addAll(List.of("1", "2", "3", "4", "5"));
        else if (player.isOp() && args.length == 2 && args[0].equalsIgnoreCase("boss")) options.addAll(List.of("bloque", "spawn", "list", "clear"));
        else if (player.isOp() && args.length == 3 && args[0].equalsIgnoreCase("boss") && args[1].equalsIgnoreCase("clear")) options.addAll(List.of("bloques", "spawn"));
        else if (player.isOp() && args.length == 2 && args[0].equalsIgnoreCase("loot")) options.addAll(List.of("add", "list", "remove"));
        else if (player.isOp() && args.length == 3 && args[0].equalsIgnoreCase("loot")) {
            options.addAll(List.of("SkeletalMinion", "SkeletalKnight", "SkeletonKing"));
            options.add(getConfig().getString("netherybreis.piglins.melee.mythic-id", "BastionNetherPiglin"));
            options.add(getConfig().getString("netherybreis.piglins.crossbow.mythic-id", "BastionNetherCrossbow"));
            options.add(getConfig().getString("netherybreis.dragon.mythic-id", "BastionRedDragon"));
            for (String kind : List.of("mutant", "giant", "moon", "king"))
                options.add(getConfig().getString("labyrinth.mobs." + kind + ".mythic-id", "BastionSlime" + kind));
        }
        else if (player.isOp() && args.length == 4 && args[0].equalsIgnoreCase("loot")) {
            if (args[1].equalsIgnoreCase("add")) options.addAll(List.of("5", "10", "25", "50", "100"));
            if (args[1].equalsIgnoreCase("remove")) {
                String mob = lootMob(args[2]);
                if (mob != null) {
                    var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(lootFile);
                    var section = cfg.getConfigurationSection("loot." + mob);
                    if (section != null) options.addAll(section.getKeys(false));
                }
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }

    // Only registered schematic files in our own directory may be started manually.
    private List<File> availableMaps() {
        File folder = new File(getDataFolder(), "schematics");
        File[] files = folder.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".schem"));
        if (files == null) return List.of();
        return Arrays.stream(files).sorted(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    private String mapId(File f) {
        String name = f.getName();
        return name.substring(0, name.length() - ".schem".length());
    }

    // Reglas independientes por schematic, tambien cuando la seleccion es aleatoria.
    // La clave es el nombre del archivo sin ".schem" (sin distinguir mayusculas).
    private String mapRulePath(String instance) {
        String fileName = schematicNames.get(instance);
        if (fileName == null) return null;
        String id = fileName.toLowerCase(Locale.ROOT);
        if (id.endsWith(".schem")) id = id.substring(0, id.length() - 6);
        // Claves sin puntos evitan que YAML interprete una extension como una subseccion.
        id = id.replace('.', '_');
        return "map-rules.maps." + id;
    }

    private double dungeonRadius(String instance) {
        double fallback = getConfig().getDouble("map-rules.defaults.radius-blocks", 33.0);
        String path = mapRulePath(instance);
        double radius = path == null ? fallback : getConfig().getDouble(path + ".radius-blocks", fallback);
        return Math.max(1.0, radius);
    }

    private void checkBelowSpawnDeath(Player player, String instance, Location destination) {
        if (player.isDead() || awaitingDungeonRespawn.contains(player.getUniqueId())
                || player.getGameMode() == GameMode.SPECTATOR) return;
        Location spawn = entryPoints.get(instance);
        String path = mapRulePath(instance);
        if (spawn == null || path == null || destination == null || destination.getWorld() != spawn.getWorld()) return;
        if (!getConfig().getBoolean(path + ".below-spawn.enabled", false)) return;
        double blocks = Math.max(1.0, getConfig().getDouble(path + ".below-spawn.blocks", 20.0));
        if (destination.getY() > spawn.getY() - blocks) return;
        // Provoca la muerte convencional para mantener drops, espectador y salida a los 5 s.
        // Algunos plugins pueden reducir/cancelar el dano: el fallback garantiza la muerte.
        double damage = Math.max(1.0, getConfig().getDouble(path + ".below-spawn.damage", 1000.0));
        player.damage(damage);
        if (!player.isDead() && player.getHealth() > 0.0
                && getConfig().getBoolean(path + ".below-spawn.force-death-if-survives", true))
            player.setHealth(0.0);
    }

    // Tambien cubre quedarse quieto bajo el umbral o teletransportes externos.
    private void checkMapVoidPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            String instance = playingWorld.get(player.getUniqueId());
            if (instance == null || !instance.equals(player.getWorld().getName())) continue;
            checkBelowSpawnDeath(player, instance, player.getLocation());
        }
    }

    private UUID leader(UUID id) { return leaderOf.getOrDefault(id, id); }
    private List<Player> members(UUID leader) {
        List<Player> result = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (leader(p.getUniqueId()).equals(leader)) result.add(p);
        return result;
    }
    private void msg(Player p, String text) { p.sendMessage(ChatColor.GOLD + "[BASTION] " + ChatColor.WHITE + format(text)); }

    private String prefix() {
        String[] colors = {"#9400FF", "#9711FF", "#9B22FF", "#9E32FF", "#A143FF", "#9E32FF", "#9B22FF", "#9711FF", "#9400FF"};
        String name = "MAZMORRAS";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length(); i++)
            out.append(net.md_5.bungee.api.ChatColor.of(colors[i])).append(net.md_5.bungee.api.ChatColor.BOLD).append(name.charAt(i));
        return out.append(org.bukkit.ChatColor.DARK_GRAY).append(" » ").toString();
    }

    private void colored(Player p, String message) { p.sendMessage(prefix() + message); }
    private void partyMessage(Player p, String message) {
        p.sendMessage(hex("#9640FC") + "" + ChatColor.BOLD + "✦ MAZMORRAS » " + ChatColor.RESET + message);
    }
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
        List<String> ids = new ArrayList<>(List.of("SkeletalMinion", "SkeletalKnight", "SkeletonKing"));
        for (String kind : List.of("mutant", "giant", "moon", "king")) {
            String mob = getConfig().getString("labyrinth.mobs." + kind + ".mythic-id", "");
            if (mob != null && !mob.isBlank()) ids.add(mob);
        }
        for (String path : List.of("netherybreis.piglins.melee.mythic-id", "netherybreis.piglins.crossbow.mythic-id", "netherybreis.dragon.mythic-id")) {
            String mob = getConfig().getString(path, "");
            if (mob != null && !mob.isBlank()) ids.add(mob);
        }
        if (raw.equalsIgnoreCase("dragon") || raw.equalsIgnoreCase("dragonrojo") || raw.equalsIgnoreCase("dragón"))
            return getConfig().getString("netherybreis.dragon.mythic-id", "BastionRedDragon");
        for (String id : ids) if (id.equalsIgnoreCase(raw)) return id;
        return null;
    }

    private boolean lootCommand(Player p, String[] args) {
        if (!p.isOp()) { msg(p, "Solo administradores OP."); return true; }
        if (args.length < 3) { msg(p, "Uso: /dungeon loot add <enemigo> <porcentaje> | list <enemigo> | remove <enemigo> <id>"); return true; }
        String mob = lootMob(args[2]);
        if (mob == null) { msg(p, "&cEnemigo desconocido. Usa TAB para ver los IDs de MythicMobs configurados."); return true; }
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
            Difficulty difficulty = worldDifficulties.getOrDefault(event.getEntity().getWorld().getName(), Difficulty.NORMAL);
            if (!isLabyrinth(event.getEntity().getWorld().getName()))
                chance = Math.min(100.0, Math.max(0.0, chance * difficultyMultiplier(difficulty, "loot-multiplier", difficulty == Difficulty.EASY ? 0.5 : difficulty == Difficulty.HARD ? 2.0 : 1.0)));
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


    // El modo aventura se identifica por nombre exacto del schematic, no por el nombre temporal bd_*.
    private boolean isAdventure(String instance) {
        String schematic = schematicNames.get(instance);
        if (schematic == null) return false;
        String id = schematic.toLowerCase(Locale.ROOT).replaceFirst("\\.schem$", "");
        return (getConfig().getBoolean("adventure.enabled", true)
                    && id.equalsIgnoreCase(getConfig().getString("adventure.map", "dungeon1-endlessxl")))
                || (getConfig().getBoolean("labyrinth.enabled", true)
                    && id.replace('_', '-').equalsIgnoreCase(getConfig().getString("labyrinth.map", "the-labyrinth").replace('_', '-')))
                || (getConfig().getBoolean("netherybreis.enabled", true)
                    && id.replace('_', '-').equalsIgnoreCase(getConfig().getString("netherybreis.map", "netherybreis").replace('_', '-')));
    }

    private boolean isLabyrinth(String instance) {
        String schematic = schematicNames.get(instance);
        if (schematic == null) return false;
        String id = schematic.toLowerCase(Locale.ROOT).replaceFirst("\\.schem$", "").replace('_', '-');
        return getConfig().getBoolean("labyrinth.enabled", true)
                && id.equalsIgnoreCase(getConfig().getString("labyrinth.map", "the-labyrinth").replace('_', '-'));
    }

    private boolean isNetherAdventure(String instance) {
        String schematic = schematicNames.get(instance);
        return schematic != null && getConfig().getBoolean("netherybreis.enabled", true)
                && schematic.replaceFirst("(?i)\\.schem$", "").replace('_', '-').equalsIgnoreCase(
                    getConfig().getString("netherybreis.map", "netherybreis").replace('_', '-'));
    }

    private String adventureConfig(String instance, String suffix) {
        return (isNetherAdventure(instance) ? "netherybreis" : isLabyrinth(instance) ? "labyrinth" : "adventure") + "." + suffix;
    }

    private String adventurePath(String instance) {
        String name = schematicNames.get(instance);
        if (name == null) return null;
        return "maps." + name.toLowerCase(Locale.ROOT).replaceFirst("\\.schem$", "").replace('.', '_');
    }

    private String locationData(Location loc) {
        return String.format(Locale.ROOT, "%.3f,%.3f,%.3f", loc.getX(), loc.getY() - PASTE_Y, loc.getZ());
    }

    private Location parseLocation(World world, String raw) {
        try {
            String[] arr = raw.split(",");
            if (arr.length != 3) return null;
            return new Location(world, Double.parseDouble(arr[0]), Double.parseDouble(arr[1]) + PASTE_Y, Double.parseDouble(arr[2]));
        } catch (RuntimeException ignored) { return null; }
    }

    private List<Encounter> adventureZones(String instance) {
        String base = adventurePath(instance);
        if (base == null) return List.of();
        var data = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(adventureFile);
        World world = Bukkit.getWorld(instance);
        if (world == null) return List.of();
        List<Encounter> result = new ArrayList<>();
        for (String zone : data.getStringList(base + ".zones")) {
            String[] parts = zone.split(";");
            if (parts.length < 2 || parts.length > 3) continue;
            Location point = parseLocation(world, parts[0]);
            if (point == null) continue;
            try { result.add(new Encounter(point, Math.max(1, Double.parseDouble(parts[1])))); }
            catch (NumberFormatException ignored) {}
        }
        return result;
    }

    private int totalEncounterWaves(String instance, int zoneIndex) {
        if (!isLabyrinth(instance) && !isNetherAdventure(instance)) return Math.max(1, getConfig().getInt("adventure.encounter.waves", 2));
        String base = adventurePath(instance);
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(adventureFile);
        List<String> zones = cfg.getStringList(base + ".zones");
        if (zoneIndex >= 0 && zoneIndex < zones.size()) {
            String[] parts = zones.get(zoneIndex).split(";");
            if (parts.length == 3) try { return Math.max(1, Math.min(10, Integer.parseInt(parts[2]) + 1)); }
            catch (NumberFormatException ignored) {}
        }
        return Math.max(1, getConfig().getInt(adventureConfig(instance, "encounter.default-extra-waves"), isNetherAdventure(instance) ? 3 : 2) + 1);
    }

    private Location adventureBossLocation(String instance) {
        String base = adventurePath(instance);
        if (base == null) return null;
        return parseLocation(Bukkit.getWorld(instance), org.bukkit.configuration.file.YamlConfiguration
                .loadConfiguration(adventureFile).getString(base + ".boss.spawn", ""));
    }

    private boolean adventureCommand(Player player, String[] args) {
        if (!player.isOp()) { msg(player, messageText("adventure-admin-denied", "&cSolo los administradores OP pueden configurar la aventura.")); return true; }
        String instance = playingWorld.get(player.getUniqueId());
        if (instance == null || !instance.equals(player.getWorld().getName()) || !isAdventure(instance)) {
            msg(player, messageText("adventure-wrong-map", "&cDebes estar dentro de EndlessXL, The Labyrinth o Netherybreis.")); return true;
        }
        String base = adventurePath(instance);
        var data = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(adventureFile);
        if (args[0].equalsIgnoreCase("mobs") || args[0].equalsIgnoreCase("radio")) {
            boolean shortSyntax = args[0].equalsIgnoreCase("radio");
            boolean addZone = shortSyntax ? args.length == 4 && args[2].equalsIgnoreCase("oleadas")
                    : args.length == 3 && args[1].equalsIgnoreCase("radio");
            if (addZone) {
                double radius;
                try { radius = Double.parseDouble(args[shortSyntax ? 1 : 2]); }
                catch (NumberFormatException ex) { msg(player, "&cRadio invalido. Ejemplo: /dungeon radio 15 oleadas 2"); return true; }
                if (!Double.isFinite(radius) || radius < 1 || radius > 100) { msg(player, "&cRadio permitido: 1 a 100 bloques."); return true; }
                int extra = (isLabyrinth(instance) || isNetherAdventure(instance))
                        ? Math.max(0, getConfig().getInt(adventureConfig(instance, "encounter.default-extra-waves"), isNetherAdventure(instance) ? 3 : 2)) : -1;
                if (shortSyntax) {
                    try { extra = Integer.parseInt(args[3]); } catch (NumberFormatException ex) { msg(player, "&cNumero de oleadas invalido."); return true; }
                    if (extra < 0 || extra > 9) { msg(player, "&cLas oleadas adicionales deben estar entre 0 y 9."); return true; }
                }
                List<String> zones = new ArrayList<>(data.getStringList(base + ".zones"));
                zones.add(locationData(player.getLocation()) + ";" + radius + (extra >= 0 ? ";" + extra : ""));
                data.set(base + ".zones", zones);
                msg(player, messageText("adventure-zone-saved", "&aZona de enemigos #{number} guardada; radio {radius} bloques.")
                        .replace("{number}", "" + zones.size()).replace("{radius}", "" + radius));
                if (extra >= 0) msg(player, "&aTotal oleadas: &f" + (1 + extra) + " &7(1 inicial + " + extra + " adicionales).");
            } else if (args.length == 2 && args[1].equalsIgnoreCase("clear")) {
                data.set(base + ".zones", null);
                msg(player, messageText("adventure-zones-cleared", "&eZonas de enemigos eliminadas de este mapa."));
            } else if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
                msg(player, "&bZonas de enemigos: &f" + data.getStringList(base + ".zones").size()); return true;
            } else { msg(player, "&e/dungeon radio 15 oleadas 2 | /dungeon mobs radio <1-100> | list | clear"); return true; }
        } else if (args[0].equalsIgnoreCase("boss")) {
            if (args.length == 2 && args[1].equalsIgnoreCase("bloque")) {
                // Marcar el bloque bajo los pies del OP. Hasta 10 activadores.
                Location ground = player.getLocation().getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getLocation();
                String marker = ground.getBlockX() + "," + (ground.getBlockY() - PASTE_Y) + "," + ground.getBlockZ();
                List<String> blocks = new ArrayList<>(data.getStringList(base + ".boss.blocks"));
                if (blocks.contains(marker)) { msg(player, "&eEste bloque ya es un activador."); return true; }
                if (blocks.size() >= 10) { msg(player, "&cYa hay 10 bloques. Usa /dungeon boss clear bloques para reiniciarlos."); return true; }
                blocks.add(marker);
                data.set(base + ".boss.blocks", blocks);
                msg(player, messageText("adventure-boss-block", "&aBloque activador del jefe: &f{number}/{limit}").replace("{number}", "" + blocks.size()).replace("{limit}", "10").replace("/3", "/10"));
            } else if (args.length == 2 && args[1].equalsIgnoreCase("spawn")) {
                if (!isSafe(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ())) {
                    msg(player, "&cNecesitas suelo seguro y espacio para que aparezca el jefe."); return true;
                }
                data.set(base + ".boss.spawn", locationData(player.getLocation()));
                msg(player, messageText("adventure-boss-spawn", "&aSpawn del jefe guardado para este mapa."));
            } else if (args.length == 2 && args[1].equalsIgnoreCase("list")) {
                msg(player, "&bBloques del jefe: &f" + data.getStringList(base + ".boss.blocks").size()
                        + " &7| Spawn: " + (data.contains(base + ".boss.spawn") ? "&aSI" : "&cNO")); return true;
            } else if (args.length == 3 && args[1].equalsIgnoreCase("clear")) {
                if (args[2].equalsIgnoreCase("bloques")) data.set(base + ".boss.blocks", null);
                else if (args[2].equalsIgnoreCase("spawn")) data.set(base + ".boss.spawn", null);
                else { msg(player, "&e/dungeon boss clear bloques|spawn"); return true; }
                msg(player, "&eConfiguracion del jefe eliminada: " + args[2]);
            } else { msg(player, "&e/dungeon boss bloque | spawn | list | clear bloques|spawn"); return true; }
        }
        try { data.save(adventureFile); }
        catch (IOException ex) { getLogger().log(java.util.logging.Level.SEVERE, "Error guardando adventure.yml", ex); msg(player, "&cNo se pudo guardar adventure.yml."); }
        return true;
    }

    private boolean adventureCheckMovement(Player player, String instance, Location from, Location to) {
        World world = player.getWorld();
        List<Encounter> zones = adventureZones(instance);
        int completed = adventureCompletedZones.getOrDefault(instance, 0);
        Integer activeIndex = adventureActiveZone.get(instance);
        if (activeIndex != null && activeIndex < zones.size()) {
            Location center = zones.get(activeIndex).center();
            double limit = Math.max(2, getConfig().getDouble(adventureConfig(instance, "encounter-boundary-blocks"), 15));
            if (horizontalDistanceSquared(center, to) > limit * limit && horizontalDistanceSquared(center, to) > horizontalDistanceSquared(center, from)) {
                msgCooldown(player, "adventure-encounter-boundary", "&cDerrota a todos los enemigos antes de avanzar.");
                return true;
            }
            return false;
        }
        if (completed < zones.size()) {
            Encounter next = zones.get(completed);
            if (horizontalDistanceSquared(next.center(), to) <= next.radius() * next.radius()) startAdventureEncounter(world, completed, next, player);
            return false;
        }
        if (adventureBossStarted.contains(instance)) return false;
        // Nether: el jefe aparece automaticamente al completar la ultima zona.
        // No se necesita pisar ningun bloque activador.
        if (isNetherAdventure(instance)) return false;
        String base = adventurePath(instance);
        var data = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(adventureFile);
        for (String marked : data.getStringList(base + ".boss.blocks")) {
            String[] parts = marked.split(",");
            if (parts.length != 3) continue;
            try {
                int x = Integer.parseInt(parts[0]), y = Integer.parseInt(parts[1]) + PASTE_Y, z = Integer.parseInt(parts[2]);
                if (to.getBlockX() == x && to.getBlockZ() == z && Math.abs(to.getBlockY() - y) <= 2) {
                    startAdventureBoss(world, player); break;
                }
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    private final Map<UUID, Long> adventureWarnings = new HashMap<>();
    private void msgCooldown(Player player, String key, String fallback) {
        long now = System.currentTimeMillis();
        if (now - adventureWarnings.getOrDefault(player.getUniqueId(), 0L) > 2000) {
            adventureWarnings.put(player.getUniqueId(), now);
            msg(player, messageText(key, fallback));
        }
    }

    // Checkpoint del parkour exclusivo del schematic de aventura. El radio evita
    // que otras masas de agua del mapa activen el retorno por accidente.
    private boolean parkourWaterCheck(Player player, String instance, Location destination) {
        if (destination == null || destination.getWorld() == null || !getConfig().getBoolean("adventure.parkour.enabled", true)) return false;
        if (parkourReturning.contains(player.getUniqueId())) return false;
        String base = adventurePath(instance);
        if (base == null) return false;
        var data = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(adventureFile);
        Location checkpoint = parseLocation(destination.getWorld(), data.getString(base + ".parkour.spawn", ""));
        if (checkpoint == null) return false;
        double radius = Math.max(1.0, getConfig().getDouble("adventure.parkour.water-detection-radius-blocks", 40.0));
        if (destination.distanceSquared(checkpoint) > radius * radius) return false;
        Block feet = destination.getBlock();
        if (feet.getType() != Material.WATER && feet.getRelative(org.bukkit.block.BlockFace.DOWN).getType() != Material.WATER) return false;
        UUID id = player.getUniqueId();
        parkourReturning.add(id);
        checkpoint.setYaw((float) data.getDouble(base + ".parkour.yaw", 0));
        checkpoint.setPitch((float) data.getDouble(base + ".parkour.pitch", 0));
        Bukkit.getScheduler().runTask(this, () -> {
            if (!player.isOnline() || !instance.equals(playingWorld.get(id))) { parkourReturning.remove(id); return; }
            player.setFallDistance(0);
            player.teleport(checkpoint);
            frozen.add(id);
            int seconds = Math.max(1, getConfig().getInt("adventure.parkour.freeze-seconds", 5));
            msg(player, messageText("adventure-parkour-return", "&b¡Caíste al agua! &fEspera {seconds}s para volver a intentarlo.")
                    .replace("{seconds}", String.valueOf(seconds)));
            Bukkit.getScheduler().runTaskLater(this, () -> {
                parkourReturning.remove(id);
                // No levantar una congelación de jefe si comenzó entretanto.
                if (!adventureBossStarted.contains(instance)) frozen.remove(id);
            }, 20L * seconds);
        });
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onAdventureLeavesDecay(LeavesDecayEvent event) {
        String instance = event.getBlock().getWorld().getName();
        if (instanceWorlds.contains(instance) && isAdventure(instance)
                && getConfig().getBoolean("adventure.leaves.prevent-decay", true)) event.setCancelled(true);
    }

    private void startAdventureEncounter(World world, int index, Encounter zone, Player trigger) {
        String instance = world.getName();
        if (adventureActiveZone.containsKey(instance) || adventureBossStarted.contains(instance)) return;
        adventureActiveZone.put(instance, index);
        adventureWave.put(instance, 1);
        // Reunir a TODOS los companeros activos en el encuentro al activarse.
        // Esto evita que alguien quede al otro lado de la barrera de 15 bloques.
        if (teamSizes.getOrDefault(instance, 1) > 1
                && getConfig().getBoolean(adventureConfig(instance, "encounter.teleport-party-on-start"), true)) {
            for (Player teammate : fighters(world)) {
                if (teammate.getUniqueId().equals(trigger.getUniqueId())) continue;
                if (!instance.equals(playingWorld.get(teammate.getUniqueId()))) continue;
                // No mover a quien ya haya entrado por su cuenta en el radio.
                if (horizontalDistanceSquared(zone.center(), teammate.getLocation()) <= zone.radius() * zone.radius()) continue;
                // Preferir el lugar del activador para que todos aparezcan juntos.
                Location safe = nearbySafeLocation(world, trigger.getLocation(), 0, 2);
                if (safe == null || horizontalDistanceSquared(zone.center(), safe) > 14 * 14)
                    safe = nearbySafeLocation(world, zone.center(), 0, 3);
                if (safe == null) {
                    getLogger().warning("No hay posicion segura para reunir a " + teammate.getName() + " en " + instance);
                    continue;
                }
                safe.setYaw(teammate.getLocation().getYaw());
                safe.setPitch(teammate.getLocation().getPitch());
                Location destination = safe.clone();
                Bukkit.getScheduler().runTask(this, () -> {
                    if (!teammate.isOnline() || !instance.equals(playingWorld.get(teammate.getUniqueId()))
                            || !Objects.equals(adventureActiveZone.get(instance), index)) return;
                    if (teammate.teleport(destination)) {
                        teammate.setFallDistance(0);
                        msg(teammate, messageText("adventure-party-gathered", "&bTu equipo ha iniciado un encuentro. &fTe reuniste con tus companeros."));
                    }
                });
            }
        }
        if (!spawnAdventureWave(world, zone, 1)) {
            adventureActiveZone.remove(instance);
            adventureWave.remove(instance);
            for (Player player : fighters(world)) msg(player, messageText("adventure-no-mobs", "&cNo se pudo iniciar el encuentro: no hay spawns seguros o MythicMobs falló."));
        }
    }

    private String labyrinthMobType(String instance) {
        double moonChance = Math.min(100.0, Math.max(0.0, getConfig().getDouble("labyrinth.mobs.moon.spawn-chance-percent", 1.0)));
        if (random.nextDouble() * 100.0 < moonChance) return "moon";
        double giantWeight = Math.max(0.0, getConfig().getDouble("labyrinth.mobs.giant.weight", 1.0));
        double mutantWeight = Math.max(0.0, getConfig().getDouble("labyrinth.mobs.mutant.weight", 3.0));
        return random.nextDouble() * Math.max(0.0001, giantWeight + mutantWeight) < giantWeight ? "giant" : "mutant";
    }

    private String labyrinthMobPath(String mobId) {
        for (String kind : List.of("mutant", "giant", "moon", "king"))
            if (mobId.equalsIgnoreCase(getConfig().getString("labyrinth.mobs." + kind + ".mythic-id", "BastionSlime" + kind)))
                return "labyrinth.mobs." + kind;
        return null;
    }

    private double labyrinthDifficultyFactor(String instance) {
        Difficulty d = worldDifficulties.getOrDefault(instance, Difficulty.NORMAL);
        return difficultyMultiplier(d, "damage-multiplier",
                d == Difficulty.EASY ? 0.5 : d == Difficulty.HARD ? 2.0 : 1.0);
    }

    // Estadisticas de Esqueletos independientes de los Slimes.
    private String skeletonMobPath(String mobId) {
        return switch (mobId) {
            case "SkeletalMinion" -> "skeleton-mobs.minion";
            case "SkeletalKnight" -> "skeleton-mobs.knight";
            case "SkeletonKing", "SkeletolKing" -> "skeleton-mobs.king";
            default -> null;
        };
    }

    private void configureSkeletonMob(LivingEntity mob, String mobId) {
        String path = skeletonMobPath(mobId);
        if (path == null) return;
        double baseHealth = mobId.equals("SkeletalMinion") ? 50.0 : mobId.equals("SkeletalKnight") ? 125.0 : 500.0;
        setEnemyHealth(mob, getConfig().getDouble(path + ".health", baseHealth));
        var scale = mob.getAttribute(org.bukkit.attribute.Attribute.SCALE);
        if (scale != null) {
            // Se configura la escala vanilla del mob; LibsDisguises puede tener limitaciones visuales.
            double defaultScale = mobId.equals("SkeletonKing") || mobId.equals("SkeletolKing")
                    ? getConfig().getDouble("boss-effects.scale", 3.0) : 1.0;
            scale.setBaseValue(Math.max(0.1, Math.min(16.0,
                    getConfig().getDouble(path + ".scale", defaultScale))));
        }
        var speed = mob.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
        if (speed != null && getConfig().contains(path + ".speed")) {
            speed.setBaseValue(Math.max(0.001, Math.min(1.0, getConfig().getDouble(path + ".speed"))));
        }
    }

    private void configureLabyrinthMob(LivingEntity mob, String mobId) {
        String path = labyrinthMobPath(mobId);
        if (path == null) return;
        setEnemyHealth(mob, getConfig().getDouble(path + ".health", 50));
        // Nunca calcular la escala sobre la caja actual de un Slime: MythicMobs
        // puede generarlos en tamaños distintos y el disfraz de jugador amplifica
        // esa diferencia. Normalizamos también los YAML antiguos Type: SLIME.
        if (mob instanceof org.bukkit.entity.Slime slime) slime.setSize(1);
        var scale = mob.getAttribute(org.bukkit.attribute.Attribute.SCALE);
        if (scale != null) {
            // Los cuatro personajes usan un disfraz de jugador (modelo de 1.8 bloques).
            // Al usar ZOMBIE como entidad base en MythicMobs, el hitbox y la
            // referencia de escala también son consistentes entre invocaciones.
            double referenceHeight = Math.max(0.1, getConfig().getDouble(
                    "labyrinth.player-model-reference-height", 1.8));
            double desiredHeight = Math.max(0.2, getConfig().getDouble(
                    path + ".height-blocks", referenceHeight));
            double multiplier = getConfig().getDouble(path + ".scale-multiplier", 1.0);
            double newScale = (desiredHeight / referenceHeight) * Math.max(0.01, multiplier);
            scale.setBaseValue(Math.max(0.1, Math.min(16.0, newScale)));
        }
        var speed = mob.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            // Velocidad indicada en config es un multiplicador, no bloques/segundo.
            double mult = Math.max(0.0, getConfig().getDouble(path + ".speed-multiplier", 1.0));
            speed.setBaseValue(Math.max(0.001, Math.min(1.0, speed.getBaseValue() * mult)));
        }
    }

    private boolean spawnAdventureWave(World world, Encounter zone, int wave) {
        String instance = world.getName();
        boolean labyrinth = isLabyrinth(instance);
        boolean nether = isNetherAdventure(instance);
        int count;
        String[] types;
        if (labyrinth || nether) {
            List<Integer> counts = getConfig().getIntegerList(nether ? "netherybreis.encounter.wave-counts" : "labyrinth.encounter.wave-counts");
            count = counts.isEmpty() ? 5 * wave : counts.get(Math.min(wave - 1, counts.size() - 1));
            count = Math.max(1, Math.min(128, count));
            types = new String[0];
        } else {
            int perPlayer = Math.max(1, getConfig().getInt("adventure.encounter.mobs-per-player",
                    getConfig().getInt("adventure.mobs-per-player", 4)));
            count = Math.min(64, perPlayer * Math.max(1, teamSizes.getOrDefault(instance, fighters(world).size())));
            types = getConfig().getStringList("adventure.mob-ids").toArray(new String[0]);
            if (types.length == 0) types = new String[]{"SkeletalMinion", "SkeletalKnight"};
        }
        Set<UUID> active = enemies.computeIfAbsent(instance, k -> new HashSet<>());
        int spawned = 0;
        for (int attempt = 0; attempt < count * 14 && spawned < count; attempt++) {
            Location pos = nearbySafeLocation(world, zone.center(), 2, Math.max(3, Math.min(8, (int) zone.radius())));
            if (pos == null) continue;
            String mobId = nether ? netherPiglinId() : labyrinth
                    ? getConfig().getString("labyrinth.mobs." + labyrinthMobType(instance) + ".mythic-id")
                    : types[random.nextInt(types.length)];
            if (mobId == null || mobId.isBlank()) continue;
            try {
                LivingEntity mob = spawnMythic(mobId, pos);
                if (nether) configureNetherPiglin(mob, mobId);
                else if (labyrinth) configureLabyrinthMob(mob, mobId);
                else configureSkeletonMob(mob, mobId);
                active.add(mob.getUniqueId()); enemyTypes.put(mob.getUniqueId(), mobId);
                updateEnemyName(mob, mobId);
                spawned++;
            } catch (Exception ex) {
                getLogger().log(java.util.logging.Level.WARNING, "No se pudo crear mob de encuentro: " + mobId, ex);
                break;
            }
        }
        if (spawned != count) {
            for (UUID id : new HashSet<>(active)) {
                Entity entity = Bukkit.getEntity(id);
                if (entity != null) entity.remove();
                active.remove(id); protectedMobs.remove(id); enemyTypes.remove(id);
            }
            return false;
        }
        if (spawned > 0) for (Player player : fighters(world))
            msg(player, messageText("adventure-encounter-wave", "&c¡Emboscada! &fOleada {wave}/{total}: &c{count} &fenemigos.")
                    .replace("{wave}", "" + wave)
                    .replace("{total}", "" + totalEncounterWaves(instance, adventureActiveZone.getOrDefault(instance, 0)))
                    .replace("{count}", "" + spawned));
        return spawned == count;
    }

    private void completeAdventureEncounter(World world) {
        String instance = world.getName();
        Integer current = adventureActiveZone.get(instance);
        if (current == null || adventureNextWavePending.contains(instance) || !enemies.getOrDefault(instance, Set.of()).isEmpty()) return;
        int wave = adventureWave.getOrDefault(instance, 1);
        int totalWaves = totalEncounterWaves(instance, current);
        if (wave < totalWaves) {
            adventureWave.put(instance, wave + 1);
            adventureNextWavePending.add(instance);
            int delay = Math.max(0, getConfig().getInt(adventureConfig(instance, "encounter.next-wave-delay-seconds"), 0));
            // Impedir activaciones dobles mientras llega la siguiente oleada.
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!instanceWorlds.contains(instance) || finishing.contains(instance)
                        || !Objects.equals(adventureActiveZone.get(instance), current)) { adventureNextWavePending.remove(instance); return; }
                if (!enemies.getOrDefault(instance, Set.of()).isEmpty()) { adventureNextWavePending.remove(instance); return; }
                List<Encounter> zones = adventureZones(instance);
                boolean success = current < zones.size() && spawnAdventureWave(world, zones.get(current), wave + 1);
                adventureNextWavePending.remove(instance);
                if (!success) {
                    getLogger().warning("No se pudieron generar enemigos de la oleada " + (wave + 1) + " en " + instance);
                    // No desbloquear la zona si MythicMobs falla al generar una oleada.
                    adventureWave.put(instance, wave);
                    for (Player player : fighters(world)) msg(player, "&cNo se pudo crear la oleada. Revisa los IDs de MythicMobs y la consola.");
                }
            }, 1L + delay * 20L);
            return;
        }
        finishAdventureEncounter(world, current);
    }

    private void finishAdventureEncounter(World world, int index) {
        String instance = world.getName();
        if (!Objects.equals(adventureActiveZone.get(instance), index)) return;
        adventureActiveZone.remove(instance); adventureWave.remove(instance); adventureNextWavePending.remove(instance);
        for (UUID id : enemies.getOrDefault(instance, Set.of())) adventureOutsideSince.remove(id);
        adventureCompletedZones.put(instance, index + 1);
        if (isNetherAdventure(instance)) {
            // Netherybreis usa oleadas y jefe automático: nunca exige boss bloque.
            // Por defecto se activa después de todas las zonas registradas,
            // o después de una zona si se configura boss-after-any-zone: true.
            boolean bossReady = getConfig().getBoolean("netherybreis.encounter.boss-after-any-zone", false)
                    || index + 1 >= adventureZones(instance).size();
            if (bossReady) {
                for (Player p : fighters(world)) msg(p, getConfig().getString(
                        "netherybreis.encounter.completed-message", "&#FF9555&l¡Oleadas completadas! &7Se aproxima el Dragón Rojo."));
                Bukkit.getScheduler().runTaskLater(this, () -> startNetherDragonCountdown(world), 20L);
            } else {
                for (Player p : fighters(world)) msg(p, getConfig().getString(
                        "netherybreis.encounter.zone-completed-message", "&a¡Oleadas de esta zona completadas!"));
            }
            return;
        }
        for (Player player : fighters(world)) msg(player, messageText("adventure-unlocked", "&a¡Zona despejada! &fPuedes seguir avanzando."));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNetherPiglinProjectile(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof org.bukkit.entity.Projectile arrow)) return;
        if (!(arrow.getShooter() instanceof LivingEntity shooter)) return;
        String instance = shooter.getWorld().getName();
        if (!isNetherAdventure(instance)) return;
        String mobId = enemyTypes.get(shooter.getUniqueId());
        if (mobId == null || !mobId.equalsIgnoreCase(getConfig().getString(
                "netherybreis.piglins.crossbow.mythic-id", "BastionNetherCrossbow"))) return;
        Difficulty diff = worldDifficulties.getOrDefault(instance, Difficulty.NORMAL);
        event.setDamage(Math.max(0, getConfig().getDouble("netherybreis.piglins.crossbow.projectile-damage", 8.0)
                * difficultyMultiplier(diff, "damage-multiplier", 1.0)));
    }

    // ===== NETHERYBREIS: piglins, countdown, Dragon Rojo y rutas persistentes =====
    private String netherPiglinId() {
        String kind = random.nextDouble() < Math.max(0, Math.min(1,
                getConfig().getDouble("netherybreis.piglins.crossbow.probability", 0.35))) ? "crossbow" : "melee";
        return getConfig().getString("netherybreis.piglins." + kind + ".mythic-id",
                kind.equals("crossbow") ? "BastionNetherCrossbow" : "BastionNetherPiglin");
    }

    private String netherMobPath(String mobId) {
        if (mobId.equalsIgnoreCase(getConfig().getString("netherybreis.piglins.melee.mythic-id", "BastionNetherPiglin"))) return "netherybreis.piglins.melee";
        if (mobId.equalsIgnoreCase(getConfig().getString("netherybreis.piglins.crossbow.mythic-id", "BastionNetherCrossbow"))) return "netherybreis.piglins.crossbow";
        if (mobId.equalsIgnoreCase(getConfig().getString("netherybreis.dragon.mythic-id", "BastionRedDragon"))) return "netherybreis.dragon";
        return null;
    }

    // Los Piglins de Dungeons no deben recoger oro, equipo ni regalos;
    // y siempre fijan jugadores como objetivo incluso si llevan oro equipado.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNetherPiglinPickup(EntityPickupItemEvent event) {
        if (!isDungeonNetherPiglin(event.getEntity())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNetherPiglinBarter(PlayerInteractEntityEvent event) {
        if (!isDungeonNetherPiglin(event.getRightClicked())) return;
        event.setCancelled(true);
    }

    private boolean isDungeonNetherPiglin(Entity entity) {
        if (entity == null || !isNetherAdventure(entity.getWorld().getName())) return false;
        String id = enemyTypes.get(entity.getUniqueId());
        return id != null && (id.equalsIgnoreCase(getConfig().getString(
                "netherybreis.piglins.melee.mythic-id", "BastionNetherPiglin"))
                || id.equalsIgnoreCase(getConfig().getString(
                "netherybreis.piglins.crossbow.mythic-id", "BastionNetherCrossbow")));
    }

    private void forceNetherPiglinAggro(LivingEntity mob, List<Player> players) {
        if (!(mob instanceof org.bukkit.entity.Mob ai) || players.isEmpty()) return;
        if (!isDungeonNetherPiglin(mob)) return;
        Player nearest = players.stream().filter(p -> !p.isDead())
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(mob.getLocation())))
                .orElse(null);
        if (nearest != null && ai.getTarget() != nearest) ai.setTarget(nearest);
    }

    private void configureNetherPiglin(LivingEntity mob, String id) {
        String crossbow = getConfig().getString("netherybreis.piglins.crossbow.mythic-id", "BastionNetherCrossbow");
        String path = "netherybreis.piglins." + (id.equalsIgnoreCase(crossbow) ? "crossbow" : "melee");
        Difficulty difficulty = worldDifficulties.getOrDefault(mob.getWorld().getName(), Difficulty.NORMAL);
        var health = mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double max = getConfig().getDouble(path + ".health", 90.0)
                * difficultyMultiplier(difficulty, "health-multiplier", 1.0);
        if (health != null) health.setBaseValue(Math.max(1, max));
        mob.setHealth(Math.min(mob.getMaxHealth(), Math.max(1, max)));
        var attack = mob.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
        if (attack != null) attack.setBaseValue(getConfig().getDouble(path + ".damage", 9.0)
                * difficultyMultiplier(difficulty, "damage-multiplier", 1.0));
        var scale = mob.getAttribute(org.bukkit.attribute.Attribute.SCALE);
        if (scale != null) scale.setBaseValue(Math.max(0.1, Math.min(8, getConfig().getDouble(path + ".scale", 1.0))));
        mob.setCanPickupItems(false);
        if (mob instanceof org.bukkit.entity.Mob ai) forceNetherPiglinAggro(mob, fighters(mob.getWorld()));
    }

    private boolean netherDragonCommand(Player p, String[] args) {
        if (!p.isOp()) { msg(p, "&cSolo los OP pueden gestionar el dragón."); return true; }
        String instance = playingWorld.get(p.getUniqueId());
        if (instance == null || !instance.equals(p.getWorld().getName()) || !isNetherAdventure(instance)) {
            msg(p, "&cEntra a una partida de Netherybreis para guardar puntos del dragón."); return true;
        }
        String base = adventurePath(instance);
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(adventureFile);
        String root = base + ".dragon";
        if (args.length >= 2 && args[1].equalsIgnoreCase("spawn")) {
            cfg.set(root + ".spawn", locationData(p.getLocation()));
            msg(p, "&aPunto de aparición del Dragón Rojo guardado.");
        } else if (args.length >= 3 && args[1].equalsIgnoreCase("punto")) {
            String sub = args[2].toLowerCase(Locale.ROOT);
            List<String> points = new ArrayList<>(cfg.getStringList(root + ".points"));
            switch (sub) {
                case "add" -> {
                    if (points.size() >= 30) { msg(p, "&cMáximo 30 puntos de vuelo."); return true; }
                    points.add(locationData(p.getLocation()));
                    cfg.set(root + ".points", points);
                    msg(p, "&aPunto de vuelo &f#" + points.size() + " &aguardado.");
                }
                case "list" -> { msg(p, "&bPuntos de vuelo: &f" + points.size() + " &7| Spawn: " + (cfg.contains(root + ".spawn") ? "&aSI" : "&cNO")); return true; }
                case "clear" -> { cfg.set(root + ".points", null); msg(p, "&ePuntos de vuelo eliminados."); }
                case "remove" -> {
                    if (args.length < 4) { msg(p, "&e/dungeon dragon punto remove <numero>"); return true; }
                    int n;
                    try { n = Integer.parseInt(args[3]); } catch (NumberFormatException ex) { msg(p, "&cNúmero inválido."); return true; }
                    if (n < 1 || n > points.size()) { msg(p, "&cEse punto no existe."); return true; }
                    points.remove(n - 1); cfg.set(root + ".points", points);
                    msg(p, "&aPunto eliminado.");
                }
                default -> { msg(p, "&e/dungeon dragon punto add|list|remove|clear"); return true; }
            }
        } else if (args.length == 2 && args[1].equalsIgnoreCase("info")) {
            msg(p, "&bDragón: &f" + cfg.getStringList(root + ".points").size() + " puntos | Spawn: " +
                    (cfg.contains(root + ".spawn") ? "&aSI" : "&cNO")); return true;
        } else if (args.length == 2 && args[1].equalsIgnoreCase("test")) {
            if (adventureBossStarted.contains(instance)) { msg(p, "&cEl jefe ya está activo."); return true; }
            startNetherDragonCountdown(p.getWorld()); return true;
        } else { msg(p, "&e/dungeon dragon spawn | punto add|list|remove <n>|clear | info | test"); return true; }
        try { cfg.save(adventureFile); }
        catch (IOException ex) { getLogger().log(java.util.logging.Level.WARNING, "No se pudo guardar ruta del dragón", ex); msg(p, "&cError al guardar los puntos."); }
        return true;
    }

    private List<Location> netherDragonPoints(String instance) {
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(adventureFile);
        String root = adventurePath(instance) + ".dragon.points";
        World world = Bukkit.getWorld(instance);
        if (world == null) return List.of();
        List<Location> points = new ArrayList<>();
        for (String raw : cfg.getStringList(root)) {
            Location l = parseLocation(world, raw);
            if (l != null) points.add(l);
        }
        return points;
    }

    private Location netherDragonSpawn(String instance) {
        World world = Bukkit.getWorld(instance);
        if (world == null) return null;
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(adventureFile);
        return parseLocation(world, cfg.getString(adventurePath(instance) + ".dragon.spawn", ""));
    }

    private void startNetherDragonCountdown(World world) {
        String instance = world.getName();
        if (!isNetherAdventure(instance) || !instanceWorlds.contains(instance)
                || adventureBossStarted.contains(instance) || finishing.contains(instance)) return;
        if (adventureActiveZone.containsKey(instance) || !enemies.getOrDefault(instance, Set.of()).isEmpty()) return;
        if (adventureZones(instance).isEmpty()) return;
        if (!getConfig().getBoolean("netherybreis.encounter.boss-after-any-zone", false)
                && adventureCompletedZones.getOrDefault(instance, 0) < adventureZones(instance).size()) return;
        if (netherDragonSpawn(instance) == null) {
            for (Player p : fighters(world)) msg(p, "&cFalta configurar /dungeon dragon spawn antes del jefe.");
            return;
        }
        // Los puntos de vuelo son opcionales. Con solo /dungeon dragon spawn
        // el jefe aparece y puede quedarse sobre el punto guardado.
        // Los puntos agregados después controlan su ruta de vuelo.
        adventureBossStarted.add(instance);
        int seconds = Math.max(1, getConfig().getInt("netherybreis.dragon.countdown-seconds", 10));
        var timer = new org.bukkit.scheduler.BukkitRunnable() {
            int left = seconds;
            @Override public void run() {
                if (!instanceWorlds.contains(instance) || finishing.contains(instance) || fighters(world).isEmpty()) {
                    netherCountdownTasks.remove(instance); adventureBossStarted.remove(instance); cancel(); return;
                }
                for (Player player : fighters(world)) player.sendTitle(
                    format(getConfig().getString("netherybreis.dragon.title", "&#FF3333&lJEFE DRAGÓN")),
                    format(getConfig().getString("netherybreis.dragon.subtitle", "&fAparecerá en &c{seconds}s")
                        .replace("{seconds}", String.valueOf(left))), 0, 25, 0);
                if (left-- <= 0) {
                    cancel(); netherCountdownTasks.remove(instance);
                    spawnNetherDragon(world);
                }
            }
        }.runTaskTimer(this, 0L, 20L);
        netherCountdownTasks.put(instance, timer);
    }

    private void spawnNetherDragon(World world) {
        String instance = world.getName();
        if (!instanceWorlds.contains(instance) || finishing.contains(instance) || fighters(world).isEmpty()) return;
        Location landing = netherDragonSpawn(instance);
        if (landing == null) { adventureBossStarted.remove(instance); return; }
        String id = getConfig().getString("netherybreis.dragon.mythic-id", "BastionRedDragon");
        String cfg = "netherybreis.dragon.entrance";
        boolean entrance = getConfig().getBoolean(cfg + ".enabled", true);
        double height = Math.max(0.0, Math.min(100.0, getConfig().getDouble(cfg + ".height-above-spawn", 20.0)));
        int duration = Math.max(1, Math.min(600, (int) Math.round(getConfig().getDouble(cfg + ".descent-seconds", 5.0) * 20)));
        // Modo espectral controlado por el plugin: la ruta atraviesa cualquier bloque
        // usando teletransporte, sin pathfinding ni destruction de estructuras.
        // Si hay techo, el modelo igualmente descendera hasta el spawn marcado.
        double usableHeight = entrance ? height : 0.0;
        Location initial = landing.clone().add(0, usableHeight, 0);
        try {
            LivingEntity dragon = spawnMythic(id, initial);
            Difficulty difficulty = worldDifficulties.getOrDefault(instance, Difficulty.NORMAL);
            var health = dragon.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            double max = Math.max(1, getConfig().getDouble("netherybreis.dragon.health", 3500)
                    * difficultyMultiplier(difficulty, "health-multiplier", 1));
            // Respetar el máximo admitido por atributos del servidor, sin provocar excepciones.
            if (health != null) {
                try { health.setBaseValue(max); }
                catch (IllegalArgumentException ex) { getLogger().warning("Vida del dragón excede el limite de atributos: " + ex.getMessage()); }
            }
            dragon.setHealth(Math.min(dragon.getMaxHealth(), max));
            var scale = dragon.getAttribute(org.bukkit.attribute.Attribute.SCALE);
            if (scale != null) scale.setBaseValue(Math.max(0.1, Math.min(8, getConfig().getDouble("netherybreis.dragon.scale", 0.5))));
            if (dragon instanceof org.bukkit.entity.Mob mob) mob.setAI(false);
            // Un mob sin IA sigue cayendo por gravedad: impedir que el controlador de vuelo pelee con la fisica.
            dragon.setGravity(false);
            dragon.setCollidable(false); // Pasaje visual por estructuras; no modifica bloques.
            dragon.setFallDistance(0);
            if (dragon instanceof org.bukkit.entity.EnderDragon vanilla) vanilla.setPhase(org.bukkit.entity.EnderDragon.Phase.HOVER);
            dragon.setRemoveWhenFarAway(false);
            bosses.computeIfAbsent(instance, k -> new HashSet<>()).add(dragon.getUniqueId());
            enemyTypes.put(dragon.getUniqueId(), id);
            updateEnemyName(dragon, id);
            for (Player player : fighters(world)) {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 0.7f);
                msg(player, getConfig().getString("netherybreis.dragon.spawn-message", "&#FF5555&l¡El Dragón Rojo ha aparecido!"));
            }
            startNetherDragonBossBar(instance, dragon);
            if (entrance && usableHeight > 1) startNetherDragonEntrance(instance, dragon, landing, duration);
            else startNetherDragonRoute(instance, dragon);
        } catch (Exception ex) {
            adventureBossStarted.remove(instance);
            getLogger().log(java.util.logging.Level.SEVERE, "No se pudo crear BastionRedDragon en " + instance, ex);
            for (Player p : fighters(world)) msg(p, "&cNo se pudo generar el dragón: revisa MythicMobs y el modelo.");
        }
    }

    private void stopNetherDragonDisplays(String instance) {
        org.bukkit.scheduler.BukkitTask entry = netherDragonEntranceTasks.remove(instance);
        if (entry != null) entry.cancel();
        org.bukkit.scheduler.BukkitTask barTask = netherDragonBarTasks.remove(instance);
        if (barTask != null) barTask.cancel();
        org.bukkit.boss.BossBar bar = netherDragonBars.remove(instance);
        if (bar != null) bar.removeAll();
    }

    private void startNetherDragonBossBar(String instance, LivingEntity dragon) {
        stopNetherDragonDisplays(instance);
        String cfg = "netherybreis.dragon.bossbar";
        if (!getConfig().getBoolean(cfg + ".enabled", true)) return;
        org.bukkit.boss.BarColor color;
        org.bukkit.boss.BarStyle style;
        try { color = org.bukkit.boss.BarColor.valueOf(getConfig().getString(cfg + ".color", "RED").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { color = org.bukkit.boss.BarColor.RED; }
        try { style = org.bukkit.boss.BarStyle.valueOf(getConfig().getString(cfg + ".style", "SOLID").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { style = org.bukkit.boss.BarStyle.SOLID; }
        org.bukkit.boss.BossBar bar = Bukkit.createBossBar("", color, style);
        netherDragonBars.put(instance, bar);
        var task = new org.bukkit.scheduler.BukkitRunnable() {
            @Override public void run() {
                if (!instanceWorlds.contains(instance) || finishing.contains(instance) || dragon.isDead() || !dragon.isValid()) {
                    cancel(); netherDragonBarTasks.remove(instance);
                    org.bukkit.boss.BossBar old = netherDragonBars.remove(instance);
                    if (old != null) old.removeAll();
                    return;
                }
                double current = Math.max(0, dragon.getHealth());
                double max = Math.max(1, dragon.getMaxHealth());
                String title = getConfig().getString(cfg + ".title", "&#FF3434&lDRAGÓN ROJO &8| &c❤ {health}/{max_health}")
                        .replace("{health}", String.valueOf((int) Math.ceil(current)))
                        .replace("{max_health}", String.valueOf((int) Math.ceil(max)));
                bar.setTitle(format(title));
                bar.setProgress(Math.max(0, Math.min(1, current / max)));
                List<Player> players = fighters(dragon.getWorld());
                for (Player p : new ArrayList<>(bar.getPlayers())) if (!players.contains(p)) bar.removePlayer(p);
                for (Player p : players) if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
            }
        }.runTaskTimer(this, 0L, 2L);
        netherDragonBarTasks.put(instance, task);
    }

    private void startNetherDragonEntrance(String instance, LivingEntity dragon, Location landing, int duration) {
        String cfg = "netherybreis.dragon.entrance";
        Location origin = dragon.getLocation().clone();
        var task = new org.bukkit.scheduler.BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!instanceWorlds.contains(instance) || finishing.contains(instance) || dragon.isDead() || !dragon.isValid()) {
                    netherDragonEntranceTasks.remove(instance); cancel(); return;
                }
                double t = Math.min(1, (++tick) / (double) duration);
                // Entrada suave sin gravedad artificial, preservando el modelo ModelEngine.
                double eased = t * t * (3 - 2 * t);
                Location next = origin.clone().add(landing.toVector().subtract(origin.toVector()).multiply(eased));
                if (!moveNetherDragon(dragon, next) && tick % 20 == 0)
                    getLogger().warning("Dragon: descenso bloqueado en " + instance);
                if (tick % 4 == 0) dragon.getWorld().spawnParticle(Particle.FLAME,
                        next.clone().add(0, 1, 0), 18, 1.5, 0.6, 1.5, 0.035);
                if (tick >= duration) {
                    moveNetherDragon(dragon, netherDragonGround(landing));
                    netherDragonEntranceTasks.remove(instance);
                    cancel();
                    netherDragonLand(instance, dragon, landing);
                    startNetherDragonRoute(instance, dragon);
                }
            }
        }.runTaskTimer(this, 1L, 1L);
        netherDragonEntranceTasks.put(instance, task);
    }

    private void netherDragonLand(String instance, LivingEntity boss, Location landing) {
        String cfg = "netherybreis.dragon.entrance";
        World world = boss.getWorld();
        world.spawnParticle(Particle.EXPLOSION, landing.clone().add(0, 1, 0), 8, 1.1, 0.7, 1.1);
        if (getConfig().getBoolean(cfg + ".fire-particles", true)) {
            world.spawnParticle(Particle.FLAME, landing.clone().add(0, 1, 0), 140, 3.0, 1.5, 3.0, 0.06);
            world.spawnParticle(Particle.LAVA, landing.clone().add(0, 1, 0), 35, 2.0, 0.5, 2.0);
        }
        world.playSound(landing, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.65f);
        world.playSound(landing, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.8f);
        double radius = Math.max(0.1, getConfig().getDouble(cfg + ".explosion-radius", 7.0));
        double damage = Math.max(0, getConfig().getDouble(cfg + ".explosion-damage", 12.0));
        Difficulty diff = worldDifficulties.getOrDefault(instance, Difficulty.NORMAL);
        damage *= difficultyMultiplier(diff, "damage-multiplier", 1.0);
        boolean nonLethal = getConfig().getBoolean(cfg + ".prevent-lethal", true);
        double knockback = Math.max(0, getConfig().getDouble(cfg + ".knockback", 0.9));
        for (Player player : fighters(world)) {
            if (player.getLocation().distanceSquared(landing) > radius * radius) continue;
            double dealt = nonLethal ? Math.min(damage, Math.max(0, player.getHealth() - 1.0)) : damage;
            if (dealt > 0) player.damage(dealt, boss);
            Vector away = player.getLocation().toVector().subtract(landing.toVector());
            away.setY(0);
            if (away.lengthSquared() > 0.001) away.normalize().multiply(knockback);
            else away.zero();
            away.setY(Math.max(0, getConfig().getDouble(cfg + ".vertical-knockback", 0.45)));
            player.setVelocity(away);
        }
        // Explosión solo audiovisual: nunca modifica bloques, fuego ni terreno.
    }

    // Ataque dirigido: alcance horizontal desde el dragon, pero permite jugadores en el suelo
    // mientras el jefe vuela sobre ellos. No depende de la IA del zombie invisible.
    private void dragonFireAttack(String instance, LivingEntity dragon, boolean resting) {
        String cfg = "netherybreis.dragon.attack";
        if (!getConfig().getBoolean(cfg + ".enabled", true)) return;
        double range = Math.max(2.0, getConfig().getDouble(cfg + ".range-blocks", 45.0));
        double vertical = Math.max(2.0, getConfig().getDouble(cfg + ".vertical-range-blocks", 55.0));
        Player target = null;
        double best = Double.MAX_VALUE;
        for (Player player : fighters(dragon.getWorld())) {
            if (player.isDead() || !player.isValid() || player.getGameMode() == GameMode.SPECTATOR) continue;
            double horizontal = horizontalDistanceSquared(dragon.getLocation(), player.getLocation());
            if (horizontal > range * range || Math.abs(dragon.getLocation().getY() - player.getLocation().getY()) > vertical) continue;
            if (horizontal < best) { best = horizontal; target = player; }
        }
        if (target == null) return;
        final Location from = dragon.getLocation().clone().add(0, 1.5, 0);
        final Location to = target.getLocation().clone().add(0, 1.0, 0);
        if (!dragonClearLine(from, to)) return;
        // Estela de fuego visible desde la cabeza del dragon hacia su victima.
        Vector line = to.toVector().subtract(from.toVector());
        double length = line.length();
        if (length > 0.01) {
            Vector step = line.clone().normalize().multiply(0.85);
            Location particle = from.clone();
            for (int i = 0, count = Math.min(90, (int) Math.ceil(length / 0.85)); i < count; i++) {
                particle.add(step);
                dragon.getWorld().spawnParticle(Particle.FLAME, particle, 3, 0.24, 0.24, 0.24, 0.02);
                dragon.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, particle, 2, 0.20, 0.20, 0.20, 0.015);
            }
        }
        dragon.getWorld().playSound(from, Sound.ENTITY_BLAZE_SHOOT, 1.4f, resting ? 0.65f : 0.85f);
        double damage = Math.max(0, getConfig().getDouble(cfg + ".damage", 10.0));
        Difficulty diff = worldDifficulties.getOrDefault(instance, Difficulty.NORMAL);
        damage *= difficultyMultiplier(diff, "damage-multiplier", 1.0);
        double splash = Math.max(0.0, getConfig().getDouble(cfg + ".splash-radius", 2.5));
        for (Player player : fighters(dragon.getWorld())) {
            if (!player.isDead() && player.getLocation().distanceSquared(target.getLocation()) <= splash * splash) {
                if (!dragonClearLine(from, player.getEyeLocation())) continue;
                if (damage > 0) { player.damage(damage, dragon); dragonSoftKnockback(player, from, cfg); }
                if (getConfig().getBoolean(cfg + ".ignite", true)) player.setFireTicks(Math.max(player.getFireTicks(),
                        Math.max(0, getConfig().getInt(cfg + ".fire-seconds", 3)) * 20));
            }
        }
        dragon.getWorld().spawnParticle(Particle.FLAME, to, 36, 1.0, 0.8, 1.0, 0.05);
    }

    // Localiza la superficie bajo el punto marcado. El OP puede señalar rutas en el aire;
    // el dragón aterriza temporalmente en la superficie mas cercana inferior.
    private Location netherDragonGround(Location marked) {
        World world = marked.getWorld();
        if (world == null) return marked.clone();
        int x = marked.getBlockX(), z = marked.getBlockZ();
        int startY = Math.min(world.getMaxHeight() - 3, Math.max(world.getMinHeight() + 2, marked.getBlockY()));
        for (int y = startY; y >= Math.max(world.getMinHeight() + 1, startY - 70); y--) {
            Block below = world.getBlockAt(x, y - 1, z);
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);
            if (below.getType().isSolid() && !below.isLiquid() && feet.isPassable() && head.isPassable()) {
                Location ground = new Location(world, x + .5, y + .15, z + .5, marked.getYaw(), 0);
                return ground;
            }
        }
        // No modificar el mapa si no hay suelo: el dragón se detiene flotando.
        return marked.clone();
    }

    private Location clampNetherDragonPoint(Location center, Location desired, double radius) {
        Vector off = desired.toVector().subtract(center.toVector());
        Vector horizontal = new Vector(off.getX(), 0, off.getZ());
        if (horizontal.lengthSquared() > radius * radius) horizontal.normalize().multiply(radius);
        Location allowed = center.clone().add(horizontal.getX(), desired.getY() - center.getY(), horizontal.getZ());
        allowed.setYaw(desired.getYaw()); allowed.setPitch(0);
        // Altura de crucero sobre el spawn. El aterrizaje usa otro destino a nivel del suelo.
        double selectedHeight = getConfig().getDouble("netherybreis.dragon.flight.cruise-height-above-spawn", 6.0);
        allowed.setY(center.getY() + Math.max(1.0, selectedHeight));
        return allowed;
    }

    // No usa navegación de mobs. Los teletransportes pasan por paredes y techos.
    private boolean moveNetherDragon(LivingEntity boss, Location target) {
        if (!boss.isValid() || boss.isDead()) return false;
        boss.setGravity(false);
        boss.setCollidable(false);
        boss.setFallDistance(0);
        boolean moved = boss.teleport(target, org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (moved) boss.setVelocity(new Vector(0, 0, 0));
        return moved;
    }

    private Player netherDragonTarget(LivingEntity boss, double range, double vertical) {
        Player closest = null;
        double best = Double.POSITIVE_INFINITY;
        Location from = boss.getLocation();
        for (Player p : fighters(boss.getWorld())) {
            if (!p.isValid() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) continue;
            double horiz = horizontalDistanceSquared(from, p.getLocation());
            if (horiz > range * range || Math.abs(from.getY() - p.getLocation().getY()) > vertical) continue;
            if (horiz < best) { best = horiz; closest = p; }
        }
        return closest;
    }

    // La trayectoria debe estar despejada: no atravesar paredes ni proyectar fuego a través de ellas.
    private boolean dragonClearLine(Location start, Location end) {
        if (start.getWorld() == null || !start.getWorld().equals(end.getWorld())) return false;
        Vector delta = end.toVector().subtract(start.toVector());
        double length = delta.length();
        if (length < 0.01) return true;
        org.bukkit.util.RayTraceResult hit = start.getWorld().rayTraceBlocks(
                start, delta.normalize(), length,
                org.bukkit.FluidCollisionMode.NEVER, true);
        return hit == null;
    }

    private void dragonSoftKnockback(Player player, Location explosion, String configBase) {
        double horiz = Math.max(0, Math.min(0.45, getConfig().getDouble(configBase + ".knockback-horizontal", 0.20)));
        double vert = Math.max(0, Math.min(0.30, getConfig().getDouble(configBase + ".knockback-vertical", 0.10)));
        Vector push = player.getLocation().toVector().subtract(explosion.toVector()).setY(0);
        if (push.lengthSquared() > 0.001) push.normalize().multiply(horiz);
        else push.zero();
        push.setY(vert);
        // El damage() de Bukkit puede aplicar su propio empuje: imponer el empuje suave al tick siguiente.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline() && !player.isDead()) player.setVelocity(push);
        }, 1L);
    }

    private void dragonGhastFireball(String instance, LivingEntity boss) {
        String base = "netherybreis.dragon.fireballs";
        if (!getConfig().getBoolean(base + ".enabled", true)) return;
        double range = getConfig().getDouble(base + ".range-blocks", 50.0);
        double vertical = getConfig().getDouble(base + ".vertical-range-blocks", 60.0);
        Player target = netherDragonTarget(boss, range, vertical);
        if (target == null) return;
        Location aim = target.getEyeLocation().clone();
        // Se origina bajo el pecho y unos bloques delante del dragon; no dentro de su hitbox.
        Location chest = boss.getLocation().clone().add(0, 2.0, 0);
        Vector dir = aim.toVector().subtract(chest.toVector());
        if (dir.lengthSquared() < 0.01) return;
        dir.normalize();
        Location muzzle = chest.clone().add(dir.clone().multiply(5.0));
        if (!muzzle.getBlock().isPassable()) {
            muzzle = boss.getLocation().clone().add(0, 2.0, 0);
            if (!muzzle.getBlock().isPassable()) return;
        }
        if (!dragonClearLine(muzzle, aim)) return; // las bolas no deben atravesar paredes
        final Vector shot = aim.toVector().subtract(muzzle.toVector()).normalize();
        double speed = Math.max(0.3, Math.min(2.0, getConfig().getDouble(base + ".speed", 1.0)));
        org.bukkit.entity.LargeFireball ball = boss.getWorld().spawn(muzzle, org.bukkit.entity.LargeFireball.class, projectile -> {
            projectile.setShooter(boss);
            projectile.setIsIncendiary(false);
            projectile.setYield(0f);
            projectile.setDirection(shot);
            projectile.setVelocity(shot.clone().multiply(speed));
        });
        dragonFireballOwners.put(ball.getUniqueId(), boss.getUniqueId());
        boss.getWorld().playSound(muzzle, Sound.ENTITY_GHAST_SHOOT, 1.5f, 0.75f);
        // Evitar que bolas sin impacto se queden para siempre.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            dragonFireballOwners.remove(ball.getUniqueId());
            if (ball.isValid()) ball.remove();
        }, 120L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNetherDragonFireballHit(org.bukkit.event.entity.ProjectileHitEvent event) {
        UUID ownerId = dragonFireballOwners.remove(event.getEntity().getUniqueId());
        if (ownerId == null) return;
        org.bukkit.entity.Projectile projectile = event.getEntity();
        Location at = projectile.getLocation();
        World world = at.getWorld();
        if (world == null) return;
        world.spawnParticle(Particle.EXPLOSION, at, 3, 0.3, 0.3, 0.3);
        world.spawnParticle(Particle.FLAME, at, 45, 1.2, 0.9, 1.2, 0.06);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.3f, 1.0f);
        String configPath = "netherybreis.dragon.fireballs";
        double radius = Math.max(0, getConfig().getDouble(configPath + ".splash-radius", 2.5));
        double damage = Math.max(0, getConfig().getDouble(configPath + ".damage", 12));
        damage *= difficultyMultiplier(worldDifficulties.getOrDefault(world.getName(), Difficulty.NORMAL), "damage-multiplier", 1);
        Entity source = Bukkit.getEntity(ownerId);
        for (Player player : fighters(world)) {
            if (player.isDead() || player.getLocation().distanceSquared(at) > radius * radius) continue;
            if (!dragonClearLine(at, player.getEyeLocation())) continue;
            if (source instanceof LivingEntity dragon) player.damage(damage, dragon);
            else player.damage(damage);
            dragonSoftKnockback(player, at, configPath);
            player.setFireTicks(Math.max(player.getFireTicks(), 20 * Math.max(0, getConfig().getInt(configPath + ".fire-seconds", 3))));
        }
        // La explosion del proyectil tiene yield=0: nunca destruye la arena.
    }

    // Busca un lugar de aterrizaje cerca de la cota del spawn, sin descender a cuevas profundas.
    // Los puntos guardados por un OP representan coordenadas horizontales de parada.
    private Location netherDragonRouteGround(Location spawn, Location waypoint) {
        World world = waypoint.getWorld();
        if (world == null) return waypoint.clone();
        int x = waypoint.getBlockX(), z = waypoint.getBlockZ();
        int top = Math.min(world.getMaxHeight() - 3, (int) Math.floor(spawn.getY()) + 4);
        int bottom = Math.max(world.getMinHeight() + 2, (int) Math.floor(spawn.getY()) - 8);
        for (int y = top; y >= bottom; y--) {
            Block below = world.getBlockAt(x, y - 1, z);
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);
            if (below.getType().isSolid() && !below.isLiquid() && feet.isPassable() && head.isPassable())
                return new Location(world, x + 0.5, y + 0.15, z + 0.5, waypoint.getYaw(), 0);
        }
        // Si no hay suelo seguro, se conserva la altura del spawn y se evita buscar cuevas.
        return new Location(world, x + 0.5, spawn.getY(), z + 0.5, waypoint.getYaw(), 0);
    }

    private void startNetherDragonRoute(String instance, LivingEntity boss) {
        org.bukkit.scheduler.BukkitTask old = netherDragonTasks.remove(instance);
        if (old != null) old.cancel();
        Location center = netherDragonSpawn(instance);
        if (center == null) center = boss.getLocation().clone();
        final Location spawnCenter = center.clone();
        String base = "netherybreis.dragon.flight";
        double radius = Math.max(4, getConfig().getDouble(base + ".max-radius-blocks", 30.0));
        List<Location> stops = new ArrayList<>();
        for (Location point : netherDragonPoints(instance))
            stops.add(clampNetherDragonPoint(spawnCenter, point, radius));
        if (stops.isEmpty()) {
            double orbit = Math.min(radius, Math.max(6, getConfig().getDouble(base + ".fallback-orbit-radius", 18)));
            for (int i = 0; i < 4; i++) {
                double angle = i * Math.PI / 2;
                stops.add(clampNetherDragonPoint(spawnCenter,
                        spawnCenter.clone().add(Math.cos(angle) * orbit, 0, Math.sin(angle) * orbit), radius));
            }
            getLogger().warning("Dragon " + instance + ": sin puntos guardados, patrulla automatica.");
        }
        final List<Location> airTargets = List.copyOf(stops);
        final List<Location> groundTargets = new ArrayList<>();
        for (Location air : airTargets) groundTargets.add(netherDragonRouteGround(spawnCenter, air));
        final long moveTicks = Math.max(20, getConfig().getLong(base + ".move-seconds", 4) * 20);
        final long descendTicks = Math.max(10, getConfig().getLong(base + ".descent-seconds", 2) * 20);
        final long ascendTicks = Math.max(10, getConfig().getLong(base + ".takeoff-seconds", 2) * 20);
        final long stopTicks = Math.max(20, getConfig().getLong(base + ".stop-seconds", 10) * 20);
        final long flightAttack = Math.max(1, getConfig().getLong("netherybreis.dragon.attack.flight-interval-seconds", 6) * 20);
        final long restAttack = Math.max(1, getConfig().getLong("netherybreis.dragon.attack.interval-seconds", 6) * 20);
        final long fireballEvery = Math.max(1, getConfig().getLong("netherybreis.dragon.fireballs.interval-seconds", 8) * 20);
        if (boss instanceof org.bukkit.entity.Mob mob) mob.setAI(false);
        boss.setGravity(false);
        boss.setCollidable(false);
        getLogger().info("[Dragon] Patrulla " + instance + ": " + airTargets.size() +
                " paradas; crucero a " + (airTargets.get(0).getY() - spawnCenter.getY()) + " bloques sobre spawn; despegue, vuelo, aterrizaje.");
        var task = new org.bukkit.scheduler.BukkitRunnable() {
            int targetIndex = 0;
            long ticks = 0;
            // 0=despegue, 1=vuelo horizontal, 2=descenso, 3=descanso en suelo
            int phase = 0;
            Location origin = boss.getLocation().clone();
            Location destination = airTargets.get(0);
            int failedMoves = 0;
            long fireballClock = 0;
            @Override public void run() {
                try {
                    if (!instanceWorlds.contains(instance) || finishing.contains(instance) || !boss.isValid() || boss.isDead()) {
                        netherDragonTasks.remove(instance); cancel(); return;
                    }
                    ticks++;
                    long duration = phase == 0 ? ascendTicks : phase == 1 ? moveTicks : descendTicks;
                    if (phase != 3) {
                        double t = Math.min(1.0, ticks / (double) duration);
                        double smooth = t * t * (3.0 - 2.0 * t);
                        Location next = origin.clone().add(destination.toVector().subtract(origin.toVector()).multiply(smooth));
                        Vector face = destination.toVector().subtract(boss.getLocation().toVector());
                        if (phase == 1 && face.lengthSquared() > 0.001)
                            next.setYaw((float) Math.toDegrees(Math.atan2(-face.getX(), face.getZ())));
                        if (!moveNetherDragon(boss, next)) {
                            if (++failedMoves % 20 == 0) getLogger().warning("[Dragon] Teleport cancelado " + failedMoves + " veces en " + instance);
                        } else failedMoves = 0;
                        if (ticks % flightAttack == 0) {
                            try { dragonFireAttack(instance, boss, false); }
                            catch (Exception attackError) { getLogger().log(java.util.logging.Level.WARNING, "[Dragon] Fallo ataque en vuelo", attackError); }
                        }
                        if (ticks >= duration) {
                            if (!moveNetherDragon(boss, destination)) return;
                            ticks = 0;
                            origin = boss.getLocation().clone();
                            if (phase == 0) { // ya ganó altura; volar al siguiente punto
                                phase = 1;
                                destination = airTargets.get(targetIndex);
                            } else if (phase == 1) { // iniciar bajada real hasta el terreno
                                phase = 2;
                                destination = groundTargets.get(targetIndex);
                            } else { // ya tocó el suelo; reposar antes del despegue
                                phase = 3;
                                boss.getWorld().spawnParticle(Particle.CLOUD, destination, 35, 1.5, 0.3, 1.5, 0.05);
                                getLogger().info("[Dragon] Aterrizo en parada " + (targetIndex + 1) + "/" + airTargets.size() + " de " + instance);
                            }
                        }
                    } else {
                        boss.setVelocity(new Vector(0, 0, 0));
                        if (ticks % restAttack == 0) {
                            try { dragonFireAttack(instance, boss, true); }
                            catch (Exception attackError) { getLogger().log(java.util.logging.Level.WARNING, "[Dragon] Fallo ataque en parada", attackError); }
                        }
                        if (ticks >= stopTicks) {
                            ticks = 0;
                            phase = 0;
                            origin = boss.getLocation().clone();
                            // Despegar verticalmente sobre la parada actual antes de ir a la siguiente.
                            destination = airTargets.get(targetIndex);
                            targetIndex = (targetIndex + 1) % airTargets.size();
                        }
                    }
                    if (++fireballClock >= fireballEvery) {
                        fireballClock = 0;
                        try { dragonGhastFireball(instance, boss); }
                        catch (Exception fireballError) { getLogger().log(java.util.logging.Level.WARNING, "[Dragon] Fallo bola de fuego", fireballError); }
                    }
                    if (ticks % 100 == 0 && failedMoves == 0)
                        getLogger().fine("[Dragon] " + instance + " fase=" + phase + " pos=" + boss.getLocation());
                } catch (Exception error) {
                    getLogger().log(java.util.logging.Level.SEVERE, "[Dragon] Error en controlador de vuelo, " + instance, error);
                    netherDragonTasks.remove(instance);
                    cancel();
                }
            }
        }.runTaskTimer(this, 1L, 1L);
        netherDragonTasks.put(instance, task);
    }

    private void startNetherDragonDeathAnimation(String instance, LivingEntity dead) {
        String cfg = "netherybreis.dragon.death";
        if (!getConfig().getBoolean(cfg + ".enabled", true)) return;
        int ticks = Math.max(20, Math.min(200, (int) Math.round(
                getConfig().getDouble(cfg + ".ascend-seconds", 4.0) * 20)));
        double height = Math.max(2, getConfig().getDouble(cfg + ".ascend-blocks", 18.0));
        Location start = dead.getLocation().clone();
        World world = dead.getWorld();
        // La entidad original ya esta muriendo: un doble visual de MythicMobs mantiene
        // el modelo mientras asciende. No participa en combate ni genera loot.
        try {
            String mythicId = getConfig().getString("netherybreis.dragon.mythic-id", "BastionRedDragon");
            LivingEntity visual = spawnMythic(mythicId, start);
            visual.setInvulnerable(true);
            visual.setGravity(false);
            visual.setSilent(true);
            visual.setRemoveWhenFarAway(false);
            if (visual instanceof org.bukkit.entity.Mob mob) mob.setAI(false);
            netherDragonDeathEntities.put(instance, visual.getUniqueId());
            var animation = new org.bukkit.scheduler.BukkitRunnable() {
                int elapsed = 0;
                @Override public void run() {
                    if (!instanceWorlds.contains(instance) || !visual.isValid()) {
                        netherDragonDeathTasks.remove(instance);
                        netherDragonDeathEntities.remove(instance);
                        if (visual.isValid()) visual.remove();
                        cancel(); return;
                    }
                    double fraction = Math.min(1, (++elapsed) / (double) ticks);
                    Location next = start.clone().add(0, height * fraction, 0);
                    next.setYaw(start.getYaw() + elapsed * 5.0f);
                    visual.teleport(next);
                    visual.setVelocity(new Vector(0, 0, 0));
                    if (elapsed % 3 == 0) {
                        world.spawnParticle(Particle.FLAME, next.clone().add(0, 1, 0), 25, 2.4, 1.3, 2.4, 0.03);
                        world.spawnParticle(Particle.END_ROD, next, 12, 2.2, 1.5, 2.2, 0.02);
                    }
                    if (elapsed >= ticks) {
                        world.spawnParticle(Particle.EXPLOSION_EMITTER, next, 2, .5, .5, .5);
                        world.spawnParticle(Particle.END_ROD, next, 5, 2.0, 1.0, 2.0);
                        world.spawnParticle(Particle.END_ROD, next, 160, 4.0, 4.0, 4.0, .25);
                        world.playSound(next, Sound.ENTITY_ENDER_DRAGON_DEATH, 2.0f, .85f);
                        world.playSound(next, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, .7f);
                        visual.remove();
                        netherDragonDeathEntities.remove(instance);
                        netherDragonDeathTasks.remove(instance);
                        cancel();
                    }
                }
            }.runTaskTimer(this, 1L, 1L);
            netherDragonDeathTasks.put(instance, animation);
        } catch (Exception ex) {
            getLogger().log(java.util.logging.Level.WARNING, "No se pudo animar la muerte del Dragon Rojo", ex);
            world.spawnParticle(Particle.EXPLOSION_EMITTER, start.clone().add(0, 3, 0), 1);
        }
    }

    private void startAdventureBoss(World world, Player trigger) {
        String instance = world.getName();
        if (adventureBossStarted.contains(instance)) return;
        Location bossAt = adventureBossLocation(instance);
        if (bossAt == null || !isSafe(world, bossAt.getBlockX(), bossAt.getBlockY(), bossAt.getBlockZ())) {
            msg(trigger, messageText("adventure-no-boss-spawn", "&cConfigura un spawn seguro con /dungeon boss spawn.")); return;
        }
        adventureBossStarted.add(instance);
        adventureBossCenters.put(instance, bossAt);
        UUID playerId = trigger.getUniqueId();
        frozen.add(playerId);
        for (Player player : fighters(world)) {
            player.sendTitle(messageText("adventure-boss-title", "&5&l¡JEFE FINAL!"), messageText("adventure-boss-subtitle", "&fAparece en 5 segundos"), 10, 70, 10);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.7f);
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            frozen.remove(playerId);
            if (!instanceWorlds.contains(instance) || finishing.contains(instance) || fighters(world).isEmpty()) return;
            boolean labyrinth = isLabyrinth(instance);
            String mobId = labyrinth ? getConfig().getString("labyrinth.mobs.king.mythic-id", "BastionSlimeKing")
                    : getConfig().getString("adventure.boss-id", "SkeletonKing");
            try {
                LivingEntity boss = spawnEpicBoss(mobId, bossAt,
                        Math.max(1, getConfig().getDouble(labyrinth ? "labyrinth.mobs.king.health" : "skeleton-mobs.king.health", 500.0)), trigger);
                if (labyrinth) configureLabyrinthMob(boss, mobId);
                else configureSkeletonMob(boss, mobId);
                bosses.computeIfAbsent(instance, k -> new HashSet<>()).add(boss.getUniqueId());
                enemyTypes.put(boss.getUniqueId(), mobId);
                updateEnemyName(boss, mobId);
                if (labyrinth) startSlimeBossJump(boss);
                if (boss instanceof org.bukkit.entity.Mob attacker) attacker.setTarget(trigger.isOnline() && trigger.getWorld().equals(world)
                        ? trigger : fighters(world).get(0));
                for (Player player : fighters(world)) msg(player, messageText("adventure-boss-appeared", "&5¡El jefe apareció! &fDerrótalo para completar la mazmorra."));
            } catch (Exception ex) {
                getLogger().log(java.util.logging.Level.SEVERE, "Error generando jefe de aventura", ex);
                adventureBossStarted.remove(instance); adventureBossCenters.remove(instance);
                for (Player player : fighters(world)) msg(player, "&cNo se pudo invocar el jefe. Revisa la consola.");
            }
        }, 100L);
    }

    private Difficulty parseManualDifficulty(String input) {
        if (input == null) return null;
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "facil", "easy" -> Difficulty.EASY;
            case "normal" -> Difficulty.NORMAL;
            case "dificil", "hard" -> Difficulty.HARD;
            default -> null;
        };
    }

    // OP-only direct map selection; does not open the voting inventory.
    private boolean adminStartMap(Player p, String desired, String requestedDifficulty) {
        if (!p.isOp()) { msg(p, "&cSolo los OP pueden seleccionar mapas manualmente."); return true; }
        List<File> schems = availableMaps();
        if (desired == null || desired.isBlank()) {
            msg(p, "&eMapas disponibles: &f" + schems.stream().map(this::mapId).collect(java.util.stream.Collectors.joining(", ")));
            msg(p, "&eUsa /dungeon map <mapa> <facil|normal|dificil>");
            return true;
        }
        if (requestedDifficulty == null) {
            msg(p, "&eElige una dificultad: /dungeon map " + desired + " <facil|normal|dificil>");
            return true;
        }
        Difficulty difficulty = parseManualDifficulty(requestedDifficulty);
        if (difficulty == null) { msg(p, "&cDificultad no válida: &f" + requestedDifficulty); return true; }
        File chosen = schems.stream().filter(f -> mapId(f).equalsIgnoreCase(desired)
                || f.getName().equalsIgnoreCase(desired)).findFirst().orElse(null);
        if (chosen == null) { msg(p, "&cMapa no encontrado: &f" + desired); return true; }
        UUID id = p.getUniqueId();
        if (!leader(id).equals(id)) { msg(p, "&cSolo el líder del equipo puede iniciar la partida."); return true; }
        List<Player> team = members(id);
        if (team.stream().anyMatch(q -> !q.isOnline() || playingWorld.containsKey(q.getUniqueId())
                || preparing.contains(q.getUniqueId()) || voterSessions.containsKey(q.getUniqueId()))) {
            msg(p, "&cUn miembro ya está en partida o en preparación."); return true;
        }
        for (Player member : team) {
            pendingDifficulties.put(member.getUniqueId(), difficulty);
            if (!bypass(member)) enteredThisWindow.add(member.getUniqueId());
            msg(member, "&aMapa: &f" + mapId(chosen) + " &7| &dDificultad: &f" + difficulty.name());
        }
        saveSchedule();
        beginLobbyCountdown(team, chosen);
        return true;
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Solo jugadores."); return true; }
        UUID id = p.getUniqueId();
        if (args.length == 0) {
            VoteSession session = voterSessions.get(id);
            if (session != null) { showVoteMenu(p, session); return true; }
            if (!leader(id).equals(id)) { msg(p, "El líder debe abrir la votación con /dungeon."); return true; }
            msg(p, "&dSelecciona una dificultad en los bloques de ruleta del Spawn.");
            return true;
        }
        if (args[0].equalsIgnoreCase("romper") || args[0].equalsIgnoreCase("libre")) {
            if (!p.isOp()) { msg(p, messageText("admin-edit-denied", "&cSolo administradores OP.")); return true; }
            if (args.length != 1) { msg(p, messageText("admin-edit-usage", "&eUsa /dungeon romper o /dungeon libre.")); return true; }
            boolean breaking = args[0].equalsIgnoreCase("romper");
            Set<UUID> modes = breaking ? adminBreakMode : adminFreeMode;
            boolean enabled = modes.add(id);
            if (!enabled) modes.remove(id);
            String key = breaking ? "admin-break-" : "admin-free-";
            String fallback = breaking
                    ? (enabled ? "&aModo romper activado. Puedes romper bloques en mazmorras." : "&cModo romper desactivado.")
                    : (enabled ? "&aModo libre activado. Sin limites de radio ni altura en mazmorras." : "&cModo libre desactivado.");
            msg(p, messageText(key + (enabled ? "on" : "off"), fallback));
            if (breaking && enabled && p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.CREATIVE) {
                msg(p, "&ePara romper bloques debes estar en Survival o Creativo. Tu modo actual: " + p.getGameMode().name());
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            if (!p.isOp()) { msg(p, "&cSolo OP puede recargar Dungeons."); return true; }
            reloadConfig();
            loadExtraConfigs();
            msg(p, messageText("admin-reload", "&aConfiguraciones de BastionDungeons recargadas."));
            return true;
        }
        if (args[0].equalsIgnoreCase("dragon")) return netherDragonCommand(p, args);
        if (args[0].equalsIgnoreCase("mobs") || args[0].equalsIgnoreCase("radio") || args[0].equalsIgnoreCase("boss")) return adventureCommand(p, args);
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
        if (args[0].equalsIgnoreCase("setspawn") && args.length == 2 && args[1].equalsIgnoreCase("parkour")) {
            if (!p.isOp()) { msg(p, messageText("adventure-admin-denied", "&cSolo administradores OP.")); return true; }
            String instance = playingWorld.get(id);
            if (instance == null || !instance.equals(p.getWorld().getName()) || !isAdventure(instance)) {
                msg(p, messageText("adventure-wrong-map", "&cDebes estar dentro de dungeon1-endlessxl.")); return true;
            }
            Location loc = p.getLocation();
            if (!isSafe(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())) {
                msg(p, "&cEl checkpoint necesita un bloque de suelo firme y espacio libre para el jugador."); return true;
            }
            String base = adventurePath(instance);
            var data = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(adventureFile);
            data.set(base + ".parkour.spawn", locationData(loc));
            data.set(base + ".parkour.yaw", (double) loc.getYaw());
            data.set(base + ".parkour.pitch", (double) loc.getPitch());
            try { data.save(adventureFile); msg(p, messageText("adventure-parkour-saved", "&bCheckpoint del parkour guardado para futuras partidas.")); }
            catch (IOException ex) { getLogger().log(java.util.logging.Level.SEVERE, "Error guardando checkpoint del parkour", ex); msg(p, "&cNo se pudo guardar adventure.yml."); }
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
                    partyMessage(target, ChatColor.WHITE + "¡Has recibido una invitación para una mazmorra de " + p.getName() + "!");
                    target.sendMessage(ChatColor.GRAY + "Usa " + ChatColor.WHITE + "/dungeon party accept" + ChatColor.GRAY + " para unirte.");
                    partyMessage(p, ChatColor.WHITE + "¡Invitación enviada correctamente!");
                }
                case "accept" -> {
                    if (!canJoin(p)) return true;
                    UUID owner = invites.remove(id);
                    if (owner == null || Bukkit.getPlayer(owner) == null || playingWorld.containsKey(owner) || preparing.contains(owner) || members(owner).size() >= 4) { msg(p,"No hay invitacion valida."); return true; }
                    if (leaderOf.containsKey(id) || playingWorld.containsKey(id) || preparing.contains(id)) { msg(p,"Ya estas en otra party o partida."); return true; }
                    leaderOf.put(id, owner);
                    partyMessage(p, ChatColor.GREEN + "¡Te has unido al equipo!");
                    for (Player member : members(owner)) {
                        if (!member.getUniqueId().equals(id))
                            partyMessage(member, ChatColor.GREEN + "¡" + p.getName() + " se ha unido al equipo!");
                    }
                }
                case "leave" -> {
                    if (playingWorld.containsKey(id) || preparing.contains(id)) { msg(p,"En partida o preparacion usa /dungeon leave."); return true; }
                    if (leader(id).equals(id)) { leaderOf.entrySet().removeIf(e -> e.getValue().equals(id)); partyMessage(p, ChatColor.WHITE + "¡Equipo disuelto!"); }
                    else { leaderOf.remove(id); partyMessage(p, ChatColor.WHITE + "¡Has salido del equipo!"); }
                }
                case "list" -> partyMessage(p, ChatColor.WHITE + "Integrantes: " + ChatColor.LIGHT_PURPLE + String.join(ChatColor.GRAY + ", " + ChatColor.LIGHT_PURPLE, members(leader(id)).stream().map(Player::getName).toList()));
                default -> msg(p,"Subcomando desconocido.");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("leave")) {
            VoteSession session = voterSessions.get(id);
            if (session != null) { cancelVote(session, "Votación cancelada: un integrante salió."); return true; }
            leave(p); return true;
        }
        if (args[0].equalsIgnoreCase("map")) {
            return adminStartMap(p, args.length >= 2 ? args[1] : null, args.length >= 3 ? args[2] : null);
        }
        if (args[0].equalsIgnoreCase("go")) {
            // OP may select a specific schematic, including outside the public opening window.
            // Normal /dungeon go remains random with the original restrictions.
            boolean selecting = args.length > 1;
            if (selecting && !p.isOp()) { msg(p, messageText("admin-map-denied", "&cSolo los OP pueden elegir un mapa.")); return true; }
            if (!selecting) { msg(p, "&dEntra a la mazmorra usando uno de los tres bloques de dificultad."); return true; }
            if (!leader(id).equals(id)) { msg(p,"Solo el lider puede iniciar."); return true; }
            List<Player> team = members(id);
            if (!selecting && team.stream().anyMatch(q -> !canJoin(q))) { msg(p, "Algún miembro no tiene acceso a esta apertura."); return true; }
            if (team.stream().anyMatch(q -> playingWorld.containsKey(q.getUniqueId()) || preparing.contains(q.getUniqueId()) || voterSessions.containsKey(q.getUniqueId()))) {
                msg(p,"Alguien ya esta en partida, preparacion o votacion."); return true;
            }
            List<File> schems = availableMaps();
            if (schems.isEmpty()) { msg(p,"Pon archivos .schem en plugins/BastionDungeons/schematics/"); return true; }
            File chosen;
            if (selecting) {
                String desired = args.length >= 3 && args[1].equalsIgnoreCase("map") ? args[2] : args.length == 2 ? args[1] : null;
                if (desired == null || desired.equalsIgnoreCase("map")) {
                    msg(p, messageText("admin-map-list", "&eMapas disponibles: &f{maps}")
                            .replace("{maps}", schems.stream().map(this::mapId).collect(java.util.stream.Collectors.joining(", "))));
                    msg(p, messageText("admin-map-usage", "&eUsa /dungeon go map <nombre>"));
                    return true;
                }
                chosen = schems.stream().filter(f -> mapId(f).equalsIgnoreCase(desired)
                        || f.getName().equalsIgnoreCase(desired)).findFirst().orElse(null);
                if (chosen == null) {
                    msg(p, messageText("admin-map-unknown", "&cMapa no encontrado: &f{map}").replace("{map}", desired));
                    return true;
                }
                msg(p, messageText("admin-map-selected", "&aMapa seleccionado: &b{map}").replace("{map}", mapId(chosen)));
            } else chosen = schems.get(random.nextInt(schems.size()));
            // Preserve the existing reservation and voting/countdown workflow.
            for (Player member : team) if (!bypass(member)) enteredThisWindow.add(member.getUniqueId());
            saveSchedule();
            // Manual OP path: optional difficulty, without any voting inventory.
            String key = args.length == 4 && args[1].equalsIgnoreCase("map") ? args[3] : "normal";
            Difficulty manualDifficulty = parseManualDifficulty(key);
            if (manualDifficulty == null) {
                msg(p, "&cDificultad no válida. Usa facil, normal o dificil."); return true;
            }
            for (Player member : team) pendingDifficulties.put(member.getUniqueId(), manualDifficulty);
            beginLobbyCountdown(team, chosen);
            return true;
        }
        msg(p,"Comando desconocido."); return true;
    }

    /** Entry point for BastionMCDungeonRoulette; skips difficulty GUI only for this entry. */
    public boolean startRouletteDungeon(Player p, String mapName, String difficultyKey) {
        if (p == null || !p.isOnline() || !p.hasPermission("bastiondungeons.use")) return false;
        if (!leader(p.getUniqueId()).equals(p.getUniqueId())) {
            msg(p, "&cSolo el líder del equipo puede activar la ruleta."); return false;
        }
        Difficulty difficulty;
        try { difficulty = Difficulty.valueOf(difficultyKey.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { msg(p, "&cDificultad inválida."); return false; }
        List<Player> team = members(p.getUniqueId());
        if (team.isEmpty() || team.stream().anyMatch(q -> !q.isOnline() || !canJoin(q))) return false;
        if (team.stream().anyMatch(q -> playingWorld.containsKey(q.getUniqueId())
                || preparing.contains(q.getUniqueId()) || voterSessions.containsKey(q.getUniqueId()))) {
            msg(p, "&cAlguien ya está en partida o en preparación."); return false;
        }
        File file = availableMaps().stream().filter(f -> mapId(f).equalsIgnoreCase(mapName)).findFirst().orElse(null);
        if (file == null) { msg(p, "&cMapa no encontrado: &f" + mapName); return false; }
        for (Player member : team) {
            pendingDifficulties.put(member.getUniqueId(), difficulty);
            if (!bypass(member)) enteredThisWindow.add(member.getUniqueId());
            msg(member, "&aRuleta: &f" + mapId(file) + " &7| &d" + difficultyKey.toUpperCase(Locale.ROOT));
        }
        saveSchedule();
        beginLobbyCountdown(team, file);
        return true;
    }

    private void openDungeonMenu(Player p) {
        VoteSession session = voterSessions.get(p.getUniqueId());
        if (session != null) { showVoteMenu(p, session); return; }
        org.bukkit.inventory.Inventory menu = Bukkit.createInventory(null, 27, previewTitle());
        for (Difficulty d : Difficulty.values()) {
            String key = "voting.options." + d.name().toLowerCase(Locale.ROOT);
            Material material = Material.matchMaterial(menusConfig.getString(key + ".material", d == Difficulty.EASY ? "LIME_WOOL" : d == Difficulty.NORMAL ? "YELLOW_WOOL" : "RED_WOOL"));
            if (material == null) material = Material.PAPER;
            menu.setItem(voteSlot(d), menuItem(material, difficultyName(d), List.of(menuText("preview.instructions", "&eUsa /dungeon go para votar"))));
        }
        p.openInventory(menu);
    }

    private org.bukkit.inventory.ItemStack menuItem(Material material, String name, List<String> lore) {
        org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(material);
        var meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void beginDifficultyVote(List<Player> team, File file) {
        VoteSession session = new VoteSession(team.get(0).getUniqueId(), team, file);
        voteSessions.put(session.leader, session);
        for (Player member : team) {
            voterSessions.put(member.getUniqueId(), session);
            preparing.add(member.getUniqueId());
            showVoteMenu(member, session);
            msg(member, messageText("vote-instructions", "Tienes 20 segundos para votar. Después habrá 5 segundos de resultados y 5 de preparación."));
        }
        session.votingSeconds = Math.max(1, getConfig().getInt("difficulty-voting.vote-duration-seconds", 20));
        session.task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (session.members.stream().anyMatch(id -> Bukkit.getPlayer(id) == null || !preparing.contains(id))) {
                cancelVote(session, messageText("vote-cancelled", "Votación cancelada: un integrante salió."));
                return;
            }
            if (!session.results) {
                if (session.votes.size() == session.members.size() || session.votingSeconds <= 0) {
                    session.results = true;
                    session.remaining = Math.max(1, getConfig().getInt("difficulty-voting.result-seconds", 5));
                } else session.votingSeconds--;
            } else if (--session.remaining <= 0) {
                int max = Arrays.stream(Difficulty.values()).mapToInt(d -> (int) session.votes.values().stream().filter(v -> v == d).count()).max().orElse(0);
                List<Difficulty> tied = Arrays.stream(Difficulty.values()).filter(d -> session.votes.values().stream().filter(v -> v == d).count() == max).toList();
                Difficulty selected = max == 0 ? Difficulty.NORMAL : tied.get(random.nextInt(tied.size()));
                for (UUID id : session.members) {
                    Player member = Bukkit.getPlayer(id);
                    pendingDifficulties.put(id, selected);
                    voterSessions.remove(id);
                    if (member != null) { member.closeInventory(); msg(member, messageText("winner", "Dificultad ganadora: {difficulty}").replace("{difficulty}", difficultyName(selected))); }
                }
                voteSessions.remove(session.leader);
                session.task.cancel();
                List<Player> players = session.members.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
                beginLobbyCountdown(players, session.schematic);
                return;
            }
            for (UUID id : session.members) {
                Player member = Bukkit.getPlayer(id);
                if (member != null && member.getOpenInventory().getTitle().equals(voteTitle())) updateVoteMenu(member, session);
            }
        }, 20L, 20L);
    }

    private void cancelVote(VoteSession session, String reason) {
        if (session.task != null) session.task.cancel();
        voteSessions.remove(session.leader);
        for (UUID id : session.members) {
            voterSessions.remove(id); pendingDifficulties.remove(id); preparing.remove(id);
            enteredThisWindow.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null) { p.closeInventory(); clearLobbyEffects(p); msg(p, reason); }
        }
        saveSchedule();
    }

    private void showVoteMenu(Player player, VoteSession session) {
        org.bukkit.inventory.Inventory menu = Bukkit.createInventory(null, 27, voteTitle());
        fillVoteMenu(menu, player, session);
        player.openInventory(menu);
    }
    private void updateVoteMenu(Player player, VoteSession session) {
        fillVoteMenu(player.getOpenInventory().getTopInventory(), player, session);
    }
    private void fillVoteMenu(org.bukkit.inventory.Inventory menu, Player player, VoteSession session) {
        for (Difficulty choice : Difficulty.values()) {
            String key = "voting.options." + choice.name().toLowerCase(Locale.ROOT);
            Material material = Material.matchMaterial(menusConfig.getString(key + ".material", choice == Difficulty.EASY ? "LIME_WOOL" : choice == Difficulty.NORMAL ? "YELLOW_WOOL" : "RED_WOOL"));
            if (material == null) material = Material.PAPER;
            List<String> lines = menusConfig.getStringList(key + ".lore");
            if (lines.isEmpty()) lines = List.of("&7Vida: &f{health}", "&7Loot: &f{loot}", "&eVotos: {votes}", "&7Votantes: {voters}", "&7{players} jugadores", "&e{seconds}s restantes");
            List<String> lore = new ArrayList<>();
            for (String line : lines) lore.add(replaceVoteVars(line, session, choice, player)
                    .replace("{health}", "" + difficultyMultiplier(choice, "health-multiplier", 1) + "x")
                    .replace("{loot}", "" + difficultyMultiplier(choice, "loot-multiplier", 1) + "x"));
            menu.setItem(voteSlot(choice), menuItem(material, difficultyName(choice), lore));
        }
        int count = session.members.size();
        String scaling = count == 1 ? menusConfig.getString("voting.scaling.solo", "&a1 jugador - Enemigos normales") : menusConfig.getString("voting.scaling.multiplayer", "&e{players} jugadores - + enemigos");
        List<String> summary = new ArrayList<>();
        for (String line : menusConfig.getStringList("voting.summary.lore"))
            summary.add(format(line.replace("{voted}", "" + session.votes.size()).replace("{players}", "" + count)
                    .replace("{seconds}", "" + Math.max(0, session.results ? session.remaining : session.votingSeconds))));
        summary.add(format(scaling.replace("{players}", "" + count)));
        Material summaryMaterial = Material.matchMaterial(menusConfig.getString("voting.summary.material", "PAPER"));
        menu.setItem(menusConfig.getInt("voting.summary.slot", 22), menuItem(summaryMaterial == null ? Material.PAPER : summaryMaterial,
                menuText("voting.summary.name", "&dVotación"), summary));
    }

    @EventHandler public void onVoteClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        String title = e.getView().getTitle();
        if (!title.equals(voteTitle()) && !title.equals(previewTitle())) return;
        e.setCancelled(true);
        if (!title.equals(voteTitle()) || !(e.getWhoClicked() instanceof Player player)) return;
        VoteSession session = voterSessions.get(player.getUniqueId());
        if (session == null || session.results || e.getClickedInventory() != e.getView().getTopInventory()) return;
        Difficulty choice = Arrays.stream(Difficulty.values()).filter(d -> voteSlot(d) == e.getRawSlot()).findFirst().orElse(null);
        if (choice == null) return;
        session.votes.put(player.getUniqueId(), choice);
        for (UUID id : session.members) {
            Player member = Bukkit.getPlayer(id);
            if (member != null && member.getOpenInventory().getTitle().equals(voteTitle())) updateVoteMenu(member, session);
        }
    }

    // Esc/E cierran el inventario en el cliente. Reabrir al siguiente tick
    // solo a quien no haya votado, mientras la votación siga activa.
    @EventHandler public void onVoteClose(InventoryCloseEvent event) {
        if (!event.getView().getTitle().equals(voteTitle()) || !(event.getPlayer() instanceof Player player)) return;
        UUID id = player.getUniqueId();
        VoteSession session = voterSessions.get(id);
        if (session == null || session.results || session.votes.containsKey(id)
                || !getConfig().getBoolean("difficulty-voting.require-vote-before-close", true)) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline() && voterSessions.get(id) == session
                    && !session.results && !session.votes.containsKey(id)
                    && !player.getOpenInventory().getTitle().equals(voteTitle())) {
                showVoteMenu(player, session);
                msg(player, messageText("vote-must-vote", "&eElige una dificultad antes de cerrar este menú."));
            }
        }, 1L);
    }

    @EventHandler public void onVoteDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (e.getView().getTitle().equals(voteTitle()) || e.getView().getTitle().equals(previewTitle())) e.setCancelled(true);
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
                        member.sendTitle(messageText("start-title", "&f☠ &#9640FC&lINICIANDO MAZMORRA &r&f☠"),
                                messageText("start-subtitle." + pendingDifficulties.getOrDefault(member.getUniqueId(), Difficulty.NORMAL).name().toLowerCase(Locale.ROOT), "&aModalidad Fácil"), 0, 40, 10);
                        // Efecto de terror al mostrar el título de inicio (una sola vez por jugador).
                        member.playSound(member.getLocation(), Sound.ENTITY_WARDEN_ROAR, 0.75f, 0.85f);
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
        if (getConfig().getBoolean("dungeon-mobs.block-natural-spawns", true)) {
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setSpawnFlags(false, false);
        }
        worldDifficulties.put(name, pendingDifficulties.getOrDefault(team.get(0).getUniqueId(), Difficulty.NORMAL));
        for (Player member : team) pendingDifficulties.remove(member.getUniqueId());
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
                            prepareNametagTravel(member, false);
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

    // Dungeon instance worlds only: never allow ambient vanilla monsters/animals.
    // MythicMobs summons through the plugin API (SpawnReason.CUSTOM), which is allowed.
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDungeonNaturalSpawn(org.bukkit.event.entity.CreatureSpawnEvent event) {
        if (!instanceWorlds.contains(event.getLocation().getWorld().getName())) return;
        if (!getConfig().getBoolean("dungeon-mobs.block-natural-spawns", true)) return;
        boolean ourMythicSummon = mythicSpawnDepth > 0 &&
                event.getSpawnReason() == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM;
        boolean otherCustomAllowed = getConfig().getBoolean("dungeon-mobs.allow-other-custom-spawns", false) &&
                event.getSpawnReason() == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM;
        if (!ourMythicSummon && !otherCustomAllowed) event.setCancelled(true);
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

    // Retirar el pasajero ANTES del teletransporte. La llamada opcional a
    // NameTangs impide que su actualizador vuelva a montarlo mientras viajamos.
    private void prepareNametagTravel(Player player, boolean dungeonExit) {
        if (dungeonExit) {
            org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin("BastionMCNameTangs");
            if (plugin != null && plugin.isEnabled()) {
                try {
                    plugin.getClass().getMethod("prepareDungeonReturn", Player.class).invoke(plugin, player);
                } catch (ReflectiveOperationException ex) {
                    getLogger().warning("NameTangs sin API de salida 1.0.10: " + ex.getClass().getSimpleName());
                }
            }
        }
        for (org.bukkit.entity.Entity passenger : new ArrayList<>(player.getPassengers())) {
            if (passenger.getScoreboardTags().contains("bastionmc_nametangs_label")) {
                player.removePassenger(passenger);
                passenger.remove();
            }
        }
    }

    // La lista de participantes no depende de world.getPlayers(): un jugador
    // puede estar muerto, reapareciendo o en modo espectador al finalizar.
    private List<Player> onlineParticipants(String name) {
        List<Player> result = new ArrayList<>();
        for (var entry : playingWorld.entrySet()) {
            if (!name.equals(entry.getValue())) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) result.add(player);
        }
        return result;
    }

    private void returnAllParticipants(String name) {
        for (Player player : onlineParticipants(name)) returnPlayer(player, name);
        // Un teletransporte fallido conserva la sesión; no destruir el mundo.
        finishInstance(name);
    }

    private void returnPlayer(Player p, String name) {
        returnPlayer(p, name, 0);
    }

    private void returnPlayer(Player p, String name, int attempt) {
        UUID id = p.getUniqueId();
        if (!name.equals(playingWorld.get(id))) return;
        // Nunca sacar a un jugador muerto antes de que reaparezca y termine su espera.
        if (awaitingDungeonRespawn.contains(id)) return;
        if (!p.isOnline()) return; // conservar sesión hasta que se gestione la desconexión
        Location back = returns.get(id);
        if (back == null || back.getWorld() == null) {
            getLogger().warning("Falta ubicación de retorno para " + p.getName() + " en " + name);
            return;
        }
        clearLobbyEffects(p);
        prepareNametagTravel(p, true);
        // Un espectador puede estar siguiendo a otra entidad.
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        // Restaurar Survival antes de cambiar de mundo: otros plugins pueden
        // reaccionar al teletransporte y conservar el modo de juego anterior.
        GameMode previousMode = p.getGameMode();
        p.setGameMode(GameMode.SURVIVAL);
        if (!p.teleport(back)) {
            p.setGameMode(previousMode);
            if (attempt < 5) {
                Bukkit.getScheduler().runTaskLater(this, () -> returnPlayer(p, name, attempt + 1), 20L);
            } else {
                getLogger().warning("No se pudo regresar a " + p.getName() + " desde " + name + " tras 6 intentos. Mundo conservado.");
                msg(p, "No se pudo regresar al Spawn. Contacta a un administrador.");
            }
            return;
        }
        p.setGameMode(GameMode.SURVIVAL);
        frozen.remove(id);
        playingWorld.remove(id);
        awaitingDungeonRespawn.remove(id);
        returns.remove(id);
        // La restauración se repite después de que finalicen los eventos de
        // teletransporte/respawn. Solo fuera de una partida y en el destino
        // esperado, para no alterar una mazmorra nueva ni otros mundos.
        Bukkit.getScheduler().runTaskLater(this, () -> restoreSurvivalAfterReturn(id, back), 1L);
        Bukkit.getScheduler().runTaskLater(this, () -> restoreSurvivalAfterReturn(id, back), 10L);
        // La limpieza se intenta otra vez cuando termine el último retorno.
        if (finishing.contains(name)) finishInstance(name);
    }

    private void restoreSurvivalAfterReturn(UUID id, Location destination) {
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline() || player.isDead()) return;
        if (playingWorld.containsKey(id) || preparing.contains(id)) return;
        if (destination.getWorld() == null || !destination.getWorld().equals(player.getWorld())) return;
        if (player.getGameMode() == GameMode.SPECTATOR) player.setSpectatorTarget(null);
        if (player.getGameMode() != GameMode.SURVIVAL) player.setGameMode(GameMode.SURVIVAL);
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
        World occupied = Bukkit.getWorld(name);
        if (occupied != null && !occupied.getPlayers().isEmpty()) {
            getLogger().warning("No se limpiará " + name + ": todavía hay jugadores dentro.");
            return;
        }
        for (UUID id : new HashSet<>(mobHolograms.keySet())) {
            Entity entity = Bukkit.getEntity(id);
            if (entity == null || name.equals(entity.getWorld().getName())) removeMobHologram(id);
        }
        for (UUID id : new HashSet<>(slimeBossTasks.keySet())) {
            Entity entity = Bukkit.getEntity(id);
            if (entity == null || name.equals(entity.getWorld().getName())) {
                slimeBossTasks.remove(id).cancel(); slimeBossJumping.remove(id);
            }
        }
        org.bukkit.scheduler.BukkitTask dragonTask = netherDragonTasks.remove(name);
        if (dragonTask != null) dragonTask.cancel();
        stopNetherDragonDisplays(name);
        org.bukkit.scheduler.BukkitTask deathAnimation = netherDragonDeathTasks.remove(name);
        if (deathAnimation != null) deathAnimation.cancel();
        UUID deathVisual = netherDragonDeathEntities.remove(name);
        if (deathVisual != null) { Entity e = Bukkit.getEntity(deathVisual); if (e != null) e.remove(); }
        org.bukkit.scheduler.BukkitTask countdownTask = netherCountdownTasks.remove(name);
        if (countdownTask != null) countdownTask.cancel();
        org.bukkit.scheduler.BukkitTask tracker = trackers.remove(name);
        if (tracker != null) tracker.cancel();
        for (UUID mob : enemies.getOrDefault(name, Set.of())) { protectedMobs.remove(mob); lostTicks.remove(mob); lastMobPositions.remove(mob); }
        for (UUID boss : bosses.getOrDefault(name, Set.of())) {
            protectedMobs.remove(boss); lostTicks.remove(boss); lastMobPositions.remove(boss);
            kingLanding.remove(boss); kingLastLaunch.remove(boss);
        }
        adventureCompletedZones.remove(name); adventureActiveZone.remove(name); adventureWave.remove(name); adventureNextWavePending.remove(name);
        // Los UUID de la instancia se limpian junto con los enemigos al terminar.
        for (UUID id : enemies.getOrDefault(name, Set.of())) adventureOutsideSince.remove(id);
        for (UUID id : new HashSet<>(parkourReturning)) {
            if (name.equals(playingWorld.get(id))) { parkourReturning.remove(id); frozen.remove(id); }
        }
        adventureBossStarted.remove(name); adventureBossCenters.remove(name); adventureReady.remove(name);
        bosses.remove(name); rounds.remove(name); roundTransitioning.remove(name);
        enemies.remove(name); bossAreas.remove(name); teamSizes.remove(name);
        entryPoints.remove(name); schematicNames.remove(name);
        totalSpawned.remove(name); defeated.remove(name); bossCountdownStarted.remove(name);
        finishing.remove(name); announcedMissing.remove(name);
        instanceWorlds.remove(name);
        worldDifficulties.remove(name); stalkTicks.remove(name);
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
            awaitingDungeonRespawn.add(e.getEntity().getUniqueId());
            e.setKeepInventory(false);
            e.setKeepLevel(false);
            // Los objetos caen al suelo para que los compañeros puedan recuperarlos.
            String worldName = playingWorld.get(e.getEntity().getUniqueId());
            Bukkit.getScheduler().runTaskLater(this, () -> {
                World world = Bukkit.getWorld(worldName);
                if (world == null || finishing.contains(worldName) || !instanceWorlds.contains(worldName)) return;
                if (!world.getPlayers().isEmpty() && fighters(world).isEmpty()) {
                    finishing.add(worldName);
                    List<Player> losers = onlineParticipants(worldName);
                    releaseParty(worldName);
                    for (Player player : losers) msg(player, "¡DERROTA! Regresando al lobby en 5 segundos...");
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        // Los muertos esperan cinco segundos DESDE su respawn individual.
                        // Los vivos pueden salir cuando finaliza la espera de derrota.
                        for (Player player : onlineParticipants(worldName)) {
                            if (!awaitingDungeonRespawn.contains(player.getUniqueId()))
                                returnPlayer(player, worldName);
                        }
                        finishInstance(worldName);
                    }, 100L);
                }
            }, 40L);
        }
    }
    // Forzar la reaparición DENTRO de la mazmorra. Otros plugins de spawn
    // pueden reemplazar el destino del evento o teletransportar al jugador
    // justo después de reaparecer.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(org.bukkit.event.player.PlayerRespawnEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        String name = playingWorld.get(id);
        if (name == null || !awaitingDungeonRespawn.contains(id)) return;
        World dungeon = Bukkit.getWorld(name);
        if (dungeon == null) return;
        Location inside = entryPoints.getOrDefault(name, dungeon.getSpawnLocation()).clone();
        e.setRespawnLocation(inside);
        Bukkit.getScheduler().runTask(this, () -> {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline() || !name.equals(playingWorld.get(id))) return;
            // El modo espectador se activa mientras el jugador sigue en la dungeon.
            player.setGameMode(GameMode.SPECTATOR);
            ensureSpectatorInsideDungeon(id, name, inside);
            player.sendMessage(ChatColor.GRAY + "Permanecerás 5 segundos como espectador dentro de la mazmorra...");
            // Corrección adicional por si otro plugin cambia el spawn un tick después.
            Bukkit.getScheduler().runTaskLater(this, () -> ensureSpectatorInsideDungeon(id, name, inside), 2L);
            Bukkit.getScheduler().runTaskLater(this, () -> ensureSpectatorInsideDungeon(id, name, inside), 10L);
            // La salida y la restauración a Survival ocurren JUNTAS tras 5 segundos.
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (!name.equals(playingWorld.get(id))) return;
                awaitingDungeonRespawn.remove(id);
                if (player.isOnline()) returnPlayer(player, name);
            }, 100L);
        });
    }

    private void ensureSpectatorInsideDungeon(UUID id, String name, Location inside) {
        if (!awaitingDungeonRespawn.contains(id) || !name.equals(playingWorld.get(id))) return;
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline() || player.isDead()) return;
        if (player.getGameMode() != GameMode.SPECTATOR) player.setGameMode(GameMode.SPECTATOR);
        if (!player.getWorld().getName().equals(name)) {
            if (!player.teleport(inside)) getLogger().warning("No se pudo mantener en la dungeon al espectador " + player.getName());
        }
    }

    // Impedir que otros plugins saquen al espectador antes de sus cinco segundos.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWaitingSpectatorTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!awaitingDungeonRespawn.contains(id)) return;
        String name = playingWorld.get(id);
        if (name == null || event.getTo() == null || event.getTo().getWorld() == null) return;
        if (!name.equals(event.getTo().getWorld().getName())) event.setCancelled(true);
    }

    // MythicMobs API is invoked reflectively to avoid requiring a paid API artifact
    // at build time. Errors are logged rather than spawning vanilla replacements.
    private LivingEntity spawnMythic(String mobId, Location location) throws Exception {
        Class<?> api = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
        Object plugin = api.getMethod("inst").invoke(null);
        Object manager = api.getMethod("getMobManager").invoke(plugin);
        Object active;
        mythicSpawnDepth++;
        try {
            active = manager.getClass().getMethod("spawnMob", String.class, Location.class)
                    .invoke(manager, mobId, location);
        } finally {
            mythicSpawnDepth--;
        }
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
            default -> {
                String path = labyrinthMobPath(mobType);
                if (path == null) path = netherMobPath(mobType);
                yield path == null ? "" : format(getConfig().getString(path + ".display-name", "&a" + mobType));
            }
        };
        String mobPath = labyrinthMobPath(mobType);
        if (mobPath == null) mobPath = netherMobPath(mobType);
        if (mobPath == null) mobPath = skeletonMobPath(mobType);
        if (mobPath != null && getConfig().contains(mobPath + ".name-format"))
            name = format(getConfig().getString(mobPath + ".name-format", "&f" + mobType));
        if (name.isEmpty()) return;
        var maxHealth = mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        double max = maxHealth == null ? mob.getHealth() : maxHealth.getValue();
        
        long hp = Math.max(0L, (long) Math.ceil(mob.getHealth() + mob.getAbsorptionAmount()));
        long total = Math.max(1L, (long) Math.ceil(max + mob.getAbsorptionAmount()));
        String namePattern = getConfig().getString("mob-nameplate.format", "{name} &7| &c❤ {health}/{max_health}");
        String rendered = format(namePattern.replace("{name}", name)
                .replace("{health}", String.valueOf(hp))
                .replace("{max_health}", String.valueOf(total)));
        if (getConfig().getBoolean("mob-hologram.enabled", true)) {
            // El nombre nativo de MythicMobs puede seguir apareciendo debajo del
            // TextDisplay cuando el jugador apunta al enemigo, aunque visible=false.
            // Para los esqueletos de EndlessXL, borrar tambien ese texto; el nombre
            // deseado ya se conserva en el holograma independiente y actualizable.
            // Se repite desde tickMobHolograms por si MythicMobs lo reasigna.
            mob.setCustomNameVisible(false);
            // El holograma independiente es el único nombre visible. También para
            // Piglins de Netherybreis, no solo esqueletos de EndlessXL.
            String nativePath = hologramPath(mobType);
            if (getConfig().getBoolean("mob-hologram.hide-native-name", true)
                    && getConfig().getBoolean(nativePath + ".hide-native-name",
                            getConfig().getBoolean("mob-hologram.hide-native-name", true))) {
                if (mob.getCustomName() != null) mob.setCustomName(null);
            }
            ensureMobHologram(mob, mobType, name, hp, total, rendered);
        } else {
            removeMobHologram(mob.getUniqueId());
            mob.setCustomName(rendered);
            mob.setCustomNameVisible(true);
        }
    }

    // Configuracion por mob: skeleton-mobs.minion.hologram / labyrinth.mobs.mutant.hologram
    private String hologramPath(String mobType) {
        String path = labyrinthMobPath(mobType);
        if (path == null) path = netherMobPath(mobType);
        return path != null ? path + ".hologram" :
                (skeletonMobPath(mobType) == null ? "mob-hologram" : skeletonMobPath(mobType) + ".hologram");
    }

    private double hologramNumber(String local, String global, double fallback) {
        return getConfig().contains(local) ? getConfig().getDouble(local) : getConfig().getDouble(global, fallback);
    }

    private void removeMobHologram(UUID mobId) {
        List<TextDisplay> lines = mobHolograms.remove(mobId);
        if (lines != null) for (TextDisplay line : lines) if (line.isValid()) line.remove();
    }

    private void ensureMobHologram(LivingEntity mob, String mobType, String name, long health, long maxHealth, String fallback) {
        String local = hologramPath(mobType);
        if (!getConfig().getBoolean(local + ".enabled", getConfig().getBoolean("mob-hologram.enabled", true))) {
            removeMobHologram(mob.getUniqueId()); return;
        }
        // Solo usar las lineas individuales cuando el administrador las define
        // explicitamente en config.yml. getStringList() sin esta comprobacion
        // tambien lee los defaults integrados en el JAR, que tenian el simbolo
        // de corazon y anulaban los cambios de mob-hologram.lines tras reload.
        List<String> patterns = getConfig().isSet(local + ".lines")
                ? getConfig().getStringList(local + ".lines")
                : getConfig().getStringList("mob-hologram.lines");
        if (patterns.isEmpty()) patterns = List.of("{name} &7| &c❤ {health}/{max_health}");
        final List<String> templates = patterns;
        List<TextDisplay> displays = mobHolograms.computeIfAbsent(mob.getUniqueId(), k -> new ArrayList<>());
        while (displays.size() > templates.size()) {
            TextDisplay old = displays.remove(displays.size() - 1);
            if (old.isValid()) old.remove();
        }
        for (int i = 0; i < templates.size(); i++) {
            final int index = i;
            String linePath = local + ".line-settings." + (i + 1);
            double defaultHeight = hologramNumber(local + ".height", "mob-hologram.height", 0.45);
            double lineSpacing = hologramNumber(local + ".line-spacing", "mob-hologram.line-spacing", 0.26);
            double height = hologramNumber(linePath + ".height", local + ".height", defaultHeight + (templates.size() - 1 - i) * lineSpacing);
            // Respectar la separacion automatica cuando no se ha definido altura individual.
            if (!getConfig().contains(linePath + ".height")) height = defaultHeight + (templates.size() - 1 - i) * lineSpacing;
            Location at = mob.getLocation().add(
                    hologramNumber(linePath + ".offset-x", "mob-hologram.offset-x", 0),
                    mob.getHeight() + height + hologramNumber(linePath + ".offset-y", "mob-hologram.offset-y", 0),
                    hologramNumber(linePath + ".offset-z", "mob-hologram.offset-z", 0));
            TextDisplay display;
            if (i >= displays.size()) {
                display = mob.getWorld().spawn(at, TextDisplay.class, hologram -> {
                    hologram.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                    hologram.setPersistent(false);
                    // Interpolar cada teletransporte entre actualizaciones de 1 tick.
                    hologram.setTeleportDuration(1);
                    hologram.setDefaultBackground(false);
                    hologram.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                    hologram.setShadowed(getConfig().getBoolean("mob-hologram.shadow", true));
                });
                displays.add(display);
            } else display = displays.get(i);
            if (!display.isValid()) continue;
            String template = templates.get(index);
            display.setText(format(template.replace("{name}", name).replace("{health}", String.valueOf(health))
                    .replace("{max_health}", String.valueOf(maxHealth)).replace("{type}", mobType)));
            int opacity = (int) Math.max(0, Math.min(255, hologramNumber(linePath + ".background-opacity", local + ".background-opacity",
                    getConfig().getDouble("mob-hologram.background-opacity", 0))));
            display.setBackgroundColor(Color.fromARGB(opacity, 0, 0, 0));
            display.setDefaultBackground(false);
            display.setSeeThrough(getConfig().getBoolean(local + ".see-through", getConfig().getBoolean("mob-hologram.see-through", false)));
            double scale = Math.max(0.1, Math.min(5, hologramNumber(linePath + ".scale", local + ".scale",
                    getConfig().getDouble("mob-hologram.scale", 1.0))));
            var transform = display.getTransformation();
            transform.getScale().set((float) scale, (float) scale, (float) scale);
            display.setTransformation(transform);
            if (!display.getWorld().equals(at.getWorld()) || display.getLocation().distanceSquared(at) > .001) display.teleport(at);
        }
    }

    private void tickMobHolograms() {
        for (UUID id : new HashSet<>(mobHolograms.keySet())) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof LivingEntity mob) || mob.isDead() || !mob.isValid()
                    || !instanceWorlds.contains(mob.getWorld().getName())) {
                removeMobHologram(id); continue;
            }
            String type = enemyTypes.get(id);
            if (type == null) { removeMobHologram(id); continue; }
            updateEnemyName(mob, type);
        }
    }

    // Rey Slime: primer salto 10 segundos despues de aparecer; luego 5 segundos tras aterrizar.
    private void startSlimeBossJump(LivingEntity boss) {
        if (!getConfig().getBoolean("labyrinth.king-jump.enabled", true)) return;
        UUID id = boss.getUniqueId();
        if (slimeBossTasks.containsKey(id)) return;
        long initial = Math.max(0L, getConfig().getLong("labyrinth.king-jump.first-delay-seconds", 10)) * 20L;
        long cooldown = Math.max(1L, getConfig().getLong("labyrinth.king-jump.repeat-delay-seconds", 5)) * 20L;
        org.bukkit.scheduler.BukkitTask task = new org.bukkit.scheduler.BukkitRunnable() {
                    long tick = 0L, nextJump = initial;
                    boolean airborne = false, climbed = false;
                    int airTicks = 0;
                    @Override public void run() {
                        if (!boss.isValid() || boss.isDead() || !instanceWorlds.contains(boss.getWorld().getName())
                                || !bosses.getOrDefault(boss.getWorld().getName(), Set.of()).contains(id)) {
                            slimeBossJumping.remove(id); slimeBossTasks.remove(id); cancel(); return;
                        }
                        tick += 2L;
                        if (!airborne && tick >= nextJump) {
                            airborne = true; climbed = false; airTicks = 0;
                            slimeBossJumping.add(id);
                            boss.setFallDistance(0);
                            double velocity = Math.max(0.4, Math.min(1.8, getConfig().getDouble("labyrinth.king-jump.jump-velocity", 1.25)));
                            boss.setVelocity(new Vector(0, velocity, 0));
                            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_SLIME_JUMP, 1.8f, .6f);
                        } else if (airborne) {
                            airTicks += 2;
                            boss.setFallDistance(0);
                            boss.getWorld().spawnParticle(Particle.BLOCK, boss.getLocation().add(0, .5, 0), 7, .5, .4, .5, 0, Material.SLIME_BLOCK.createBlockData());
                            if (!boss.isOnGround()) climbed = true;
                            // Watchdog: si una IA bloquea la velocidad, no se queda congelada la habilidad.
                            if ((climbed && boss.isOnGround() && airTicks >= 8) || airTicks > 120) {
                                airborne = false;
                                slimeBossJumping.remove(id);
                                slimeBossImpact(boss);
                                nextJump = tick + cooldown;
                            }
                        }
                    }
                }.runTaskTimer(this, 2L, 2L);
        slimeBossTasks.put(id, task);
    }

    private void slimeBossImpact(LivingEntity boss) {
        if (!boss.isValid() || boss.isDead()) return;
        String base = "labyrinth.king-jump.";
        Location at = boss.getLocation();
        double radius = Math.max(.5, Math.min(25, getConfig().getDouble(base + "explosion-radius", 5.0)));
        double normalDamage = Math.max(0, getConfig().getDouble(base + "explosion-damage", 14.0));
        double difficulty = labyrinthDifficultyFactor(boss.getWorld().getName());
        boss.getWorld().spawnParticle(Particle.EXPLOSION, at.clone().add(0, .6, 0), 7, radius / 2, .5, radius / 2, 0);
        boss.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, .4, 0), 90, radius / 2, .3, radius / 2, .09);
        boss.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, .8f);
        for (Player player : fighters(boss.getWorld())) {
            if (player.isDead() || player.getLocation().distanceSquared(at) > radius * radius) continue;
            double damage = normalDamage * difficulty;
            if (getConfig().getBoolean(base + "prevent-lethal", true)) damage = Math.min(damage, Math.max(0, player.getHealth() - 1.0));
            if (damage > 0) player.damage(damage);
            Vector push = player.getLocation().toVector().subtract(at.toVector());
            push.setY(0);
            if (push.lengthSquared() > .001) push.normalize().multiply(getConfig().getDouble(base + "knockback", .9));
            push.setY(getConfig().getDouble(base + "vertical-knockback", .45));
            player.setVelocity(push);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSlimeBossFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL
                && slimeBossJumping.contains(event.getEntity().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onNetherDragonPhaseDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.SUFFOCATION) return;
        String instance = event.getEntity().getWorld().getName();
        if (!instanceWorlds.contains(instance)) return;
        UUID uuid = event.getEntity().getUniqueId();
        if (bosses.getOrDefault(instance, Set.of()).contains(uuid)
                && getConfig().getString("netherybreis.dragon.mythic-id", "BastionRedDragon")
                    .equalsIgnoreCase(enemyTypes.getOrDefault(uuid, ""))) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEnemyDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob)) return;
        String world = mob.getWorld().getName();
        if (!instanceWorlds.contains(world)) return;
        UUID id = mob.getUniqueId();
        String type = bosses.getOrDefault(world, Set.of()).contains(id) ? enemyTypes.getOrDefault(id, "SkeletonKing") :
                enemies.getOrDefault(world, Collections.emptySet()).contains(id) ?
                (enemyTypes.getOrDefault(id, "SkeletalMinion")) : null;
        if (type == null) return;
        // Esperar un tick para leer la vida DESPUES del daño aplicado por Bukkit/MythicMobs.
        Bukkit.getScheduler().runTask(this, () -> {
            if (mob.isValid() && !mob.isDead()) updateEnemyName(mob, type);
        });
    }

    /**
     * Entrada de jefe de MythicMobs. No altera su ID, las habilidades, los drops ni
     * la lógica de victoria ya existentes. El PNG es procesado por LibsDisguises:
     * se configura la skin registrada, no una URL ni un PNG sin registrar.
     */
    private LivingEntity spawnEpicBoss(String mobId, Location spawn, double health, Player target) throws Exception {
        boolean king = mobId.equalsIgnoreCase("SkeletonKing") || mobId.equalsIgnoreCase("SkeletolKing");
        boolean enabled = king && getConfig().getBoolean("boss-effects.enabled", true);
        Location landing = spawn.clone();
        double drop = enabled ? Math.max(0, Math.min(20, getConfig().getDouble("boss-effects.entrance.drop-blocks", 10))) : 0;
        Location at = landing.clone().add(0, drop, 0);
        LivingEntity boss = spawnMythic(mobId, at);
        setEnemyHealth(boss, health);
        if (enabled) {
            double scale = Math.max(0.5, Math.min(4.0, getConfig().getDouble("boss-effects.scale", 3.0)));
            var scaleAttribute = boss.getAttribute(org.bukkit.attribute.Attribute.SCALE);
            if (scaleAttribute != null) scaleAttribute.setBaseValue(scale);
            applyKingSkin(boss);
            if (drop > 0.1) {
                kingLanding.add(boss.getUniqueId());
                boss.setInvulnerable(true);
                boss.setGravity(true);
                boss.setVelocity(new org.bukkit.util.Vector(0, -0.42, 0));
                Bukkit.getScheduler().runTaskTimer(this, new java.util.function.Consumer<org.bukkit.scheduler.BukkitTask>() {
                    int ticks = 0;
                    @Override public void accept(org.bukkit.scheduler.BukkitTask task) {
                        if (!boss.isValid() || boss.isDead() || !boss.getWorld().equals(landing.getWorld())) {
                            kingLanding.remove(boss.getUniqueId()); task.cancel(); return;
                        }
                        ticks += 2;
                        if (!boss.isOnGround() && ticks < 100) {
                            boss.setFallDistance(0);
                            boss.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, boss.getLocation(), 5, .5, .3, .5, .02);
                            return;
                        }
                        if (ticks >= 100 && !boss.isOnGround()) boss.teleport(landing);
                        kingLanding.remove(boss.getUniqueId());
                        boss.setFallDistance(0);
                        boss.setInvulnerable(false);
                        kingImpact(boss.getLocation());
                        if (boss instanceof org.bukkit.entity.Mob attacker && target != null && target.isOnline()
                                && target.getWorld().equals(boss.getWorld())) attacker.setTarget(target);
                        task.cancel();
                    }
                }, 2L, 2L);
            }
            startKingTeleport(boss);
        }
        return boss;
    }

    private void kingImpact(Location pos) {
        World world = pos.getWorld();
        if (world == null) return;
        world.spawnParticle(Particle.EXPLOSION, pos.clone().add(0, 1, 0), 3, .5, .3, .5, .01);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, pos.clone().add(0, 0.4, 0), 75, 2, .25, 2, .07);
        world.spawnParticle(Particle.SMOKE, pos.clone().add(0, .6, 0), 55, 2, .35, 2, .03);
        world.playSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.65f);
        world.playSound(pos, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, .7f);
        double radius = Math.max(0, getConfig().getDouble("boss-effects.entrance.impact-knockback-radius", 3.5));
        for (Player player : fighters(world)) {
            if (player.getLocation().distanceSquared(pos) > radius * radius) continue;
            org.bukkit.util.Vector push = player.getLocation().toVector().subtract(pos.toVector()).setY(0);
            if (push.lengthSquared() < .01) push = new org.bukkit.util.Vector(1, 0, 0);
            player.setVelocity(push.normalize().multiply(.8).setY(.65));
        }
    }

    private void applyKingSkin(LivingEntity boss) {
        if (!getConfig().getBoolean("boss-effects.skin.enabled", true)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) {
            getLogger().warning("SkeletolKing: LibsDisguises no está activo. El jefe seguirá siendo MythicMob."); return;
        }
        String skin = getConfig().getString("boss-effects.skin.registered-name", "BastionKingSkin");
        if (skin == null || skin.isBlank()) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!boss.isValid() || boss.isDead()) return;
            try {
                Class<?> playerDisguiseType = Class.forName("me.libraryaddict.disguise.disguisetypes.PlayerDisguise");
                Object disguise = playerDisguiseType.getConstructor(String.class).newInstance("SkeletolKing");
                playerDisguiseType.getMethod("setSkin", String.class).invoke(disguise, skin);
                Class<?> disguiseType = Class.forName("me.libraryaddict.disguise.disguisetypes.Disguise");
                Class<?> api = Class.forName("me.libraryaddict.disguise.DisguiseAPI");
                api.getMethod("disguiseToAll", Entity.class, disguiseType).invoke(null, boss, disguise);
            } catch (Exception ex) {
                getLogger().warning("No fue posible aplicar la skin '" + skin + "': " + ex.getMessage()
                        + ". Comprueba /grabskin y la versión de LibsDisguises.");
            }
        }, 5L);
    }

    private void startKingTeleport(LivingEntity boss) {
        if (!getConfig().getBoolean("boss-effects.teleport.enabled", true)) return;
        int interval = Math.max(60, getConfig().getInt("boss-effects.teleport.interval-ticks", 180));
        Bukkit.getScheduler().runTaskTimer(this, new java.util.function.Consumer<org.bukkit.scheduler.BukkitTask>() {
            @Override public void accept(org.bukkit.scheduler.BukkitTask task) {
                if (!boss.isValid() || boss.isDead() || !instanceWorlds.contains(boss.getWorld().getName())) {
                    task.cancel(); return;
                }
                if (kingLanding.contains(boss.getUniqueId())) return;
                if (random.nextDouble() > Math.max(0, Math.min(1, getConfig().getDouble("boss-effects.teleport.chance", .40)))) return;
                List<Player> players = fighters(boss.getWorld());
                if (players.isEmpty()) return;
                Player target = players.get(random.nextInt(players.size()));
                org.bukkit.util.Vector facing = target.getLocation().getDirection().setY(0);
                if (facing.lengthSquared() < .01) return;
                double distance = Math.max(1.5, getConfig().getDouble("boss-effects.teleport.behind-distance", 3.5));
                Location behind = target.getLocation().clone().subtract(facing.normalize().multiply(distance));
                if (!isSafe(behind.getWorld(), behind.getBlockX(), behind.getBlockY(), behind.getBlockZ())) return;
                behind.setDirection(target.getLocation().toVector().subtract(behind.toVector()));
                boss.getWorld().spawnParticle(Particle.PORTAL, boss.getLocation().add(0, 1, 0), 36, .5, .9, .5, .3);
                boss.teleport(behind);
                boss.getWorld().spawnParticle(Particle.PORTAL, behind.clone().add(0, 1, 0), 36, .5, .9, .5, .3);
                boss.getWorld().playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, .75f);
                if (boss instanceof org.bukkit.entity.Mob attacker) attacker.setTarget(target);
            }
        }, interval, interval);
    }

    @EventHandler(ignoreCancelled = true)
    public void onKingMeleeHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof LivingEntity boss)) return;
        if (!bosses.getOrDefault(boss.getWorld().getName(), Set.of()).contains(boss.getUniqueId())) return;
        if (kingLanding.contains(boss.getUniqueId()) || isLabyrinth(boss.getWorld().getName())) return;
        if (!getConfig().getBoolean("boss-effects.launch.enabled", true)) return;
        long now = System.currentTimeMillis();
        long last = kingLastLaunch.getOrDefault(boss.getUniqueId(), 0L);
        long cooldown = Math.max(250, getConfig().getLong("boss-effects.launch.cooldown-ms", 2500));
        if (now - last < cooldown) return;
        kingLastLaunch.put(boss.getUniqueId(), now);
        Bukkit.getScheduler().runTask(this, () -> {
            if (!victim.isOnline() || victim.isDead() || !victim.getWorld().equals(boss.getWorld())) return;
            org.bukkit.util.Vector away = victim.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0);
            if (away.lengthSquared() < .01) away = victim.getLocation().getDirection();
            away.normalize().multiply(getConfig().getDouble("boss-effects.launch.horizontal", 1.1));
            away.setY(getConfig().getDouble("boss-effects.launch.vertical", .8));
            victim.setVelocity(away);
            victim.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, victim.getLocation(), 14, .3, .6, .3, .05);
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onConfiguredDungeonMobHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof LivingEntity attacker)) return;
        String world = attacker.getWorld().getName();
        if (!instanceWorlds.contains(world)) return;
        String type = enemyTypes.get(attacker.getUniqueId());
        if (type == null) return;
        String path = isLabyrinth(world) ? labyrinthMobPath(type) : skeletonMobPath(type);
        if (path == null) return;
        // Solo ataques directos: las habilidades personalizadas de MythicMobs son independientes.
        double normalDamage = getConfig().getDouble(path + ".damage", 10.0);
        double factor = isLabyrinth(world) ? labyrinthDifficultyFactor(world)
                : difficultyMultiplier(worldDifficulties.getOrDefault(world, Difficulty.NORMAL),
                        "damage-multiplier", 1.0);
        event.setDamage(Math.max(0, normalDamage * factor));
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

    // HIGHEST para que el permiso temporal de un OP se aplique tambien cuando
    // otra proteccion haya cancelado previamente la rotura de este bloque.
    // No se modifica ningun mundo ajeno a las instancias de Dungeons.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDungeonBreak(BlockBreakEvent event) {
        if (!dungeonWorld(event.getBlock().getWorld())) return;
        Player player = event.getPlayer();
        if (player.isOp() && adminBreakMode.contains(player.getUniqueId())
                && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.CREATIVE)) {
            event.setCancelled(false);
            return;
        }
        if (getConfig().getBoolean("dungeon-protection.block-breaking", true)) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onDungeonPlace(BlockPlaceEvent event) { if (dungeonWorld(event.getBlock().getWorld()) && getConfig().getBoolean("dungeon-protection.block-placing", true)) event.setCancelled(true); }
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
        if (getConfig().getBoolean("dungeon-protection.block-ender-pearls", true)
                && event.getItem() != null && event.getItem().getType() == Material.ENDER_PEARL) {
            event.setCancelled(true); return;
        }
        if (getConfig().getBoolean("dungeon-protection.block-interactions", true)
                && (event.getClickedBlock() != null || event.getAction() == org.bukkit.event.block.Action.PHYSICAL))
            event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onDungeonPearl(org.bukkit.event.player.PlayerTeleportEvent event) {
        if (event.getCause() == org.bukkit.event.player.PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && dungeonWorld(event.getPlayer().getWorld())
                && getConfig().getBoolean("dungeon-protection.block-ender-pearls", true)) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onDungeonEntityInteract(PlayerInteractEntityEvent event) {
        if (dungeonWorld(event.getPlayer().getWorld()) && getConfig().getBoolean("dungeon-protection.block-interactions", true)) event.setCancelled(true);
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
        if (instance != null && instance.equals(player.getWorld().getName()))
            checkBelowSpawnDeath(player, instance, event.getTo());
        if (player.isDead() || awaitingDungeonRespawn.contains(player.getUniqueId())) return;
        if (instance != null && instance.equals(player.getWorld().getName())
                && player.getGameMode() != GameMode.SPECTATOR
                && !(player.isOp() && adminFreeMode.contains(player.getUniqueId()))) {
            Location center = entryPoints.get(instance);
            Location destination = event.getTo();
            if (center != null && destination.getWorld().equals(center.getWorld())) {
                double dx = destination.getX() - center.getX();
                double dz = destination.getZ() - center.getZ();
                Location limitCenter = adventureBossStarted.contains(instance) ? adventureBossCenters.getOrDefault(instance, center) : center;
                double lx = destination.getX() - limitCenter.getX(), lz = destination.getZ() - limitCenter.getZ();
                double limitRadius = adventureBossStarted.contains(instance)
                        ? Math.max(1.0, getConfig().getDouble("adventure.boss-radius-blocks", 100.0))
                        : isAdventure(instance) ? Math.max(33.0, getConfig().getDouble("adventure.travel-radius-blocks", 256.0))
                        : dungeonRadius(instance);
                if ((getConfig().getBoolean("height-protection.enabled", true) && !adventureBossStarted.contains(instance)
                        && !isAdventure(instance)
                        && destination.getY() > center.getY() + getConfig().getDouble("height-protection.max-height-above-spawn", 10.0))
                        || lx * lx + lz * lz > limitRadius * limitRadius) {
                    // Programar el teleport fuera de PlayerMoveEvent para evitar reentradas.
                    Bukkit.getScheduler().runTask(this, () -> {
                        if (instance.equals(playingWorld.get(player.getUniqueId())) && player.isOnline()) {
                            player.teleport((adventureBossStarted.contains(instance)
                                    ? adventureBossCenters.getOrDefault(instance, center) : center).clone());
                            msg(player, messageText("boundary", "No puedes salir de los límites de la mazmorra."));
                        }
                    });
                    event.setTo(event.getFrom());
                    return;
                }
            }
        }
        if (instance != null && isAdventure(instance) && adventureReady.contains(instance)
                && !finishing.contains(instance) && player.getGameMode() != GameMode.SPECTATOR
                && parkourWaterCheck(player, instance, event.getTo())) {
            event.setTo(event.getFrom());
            return;
        }
        if (instance != null && isAdventure(instance) && adventureReady.contains(instance)
                && !finishing.contains(instance) && !adventureBossStarted.contains(instance)
                && player.getGameMode() != GameMode.SPECTATOR
                && !(player.isOp() && adminFreeMode.contains(player.getUniqueId()))) {
            if (adventureCheckMovement(player, instance, event.getFrom(), event.getTo())) {
                event.setTo(event.getFrom());
                return;
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
                    if (isAdventure(name)) {
                        adventureCompletedZones.put(name, 0);
                        adventureReady.add(name);
                        for (Player player : world.getPlayers()) msg(player, messageText("adventure-start", "&bAventura: avanza y derrota a los enemigos de cada zona."));
                    } else spawnNextMobs(world);
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
        Difficulty difficulty = worldDifficulties.getOrDefault(mob.getWorld().getName(), Difficulty.NORMAL);
        hp *= difficultyMultiplier(difficulty, "health-multiplier", difficulty == Difficulty.EASY ? 0.5 : difficulty == Difficulty.HARD ? 2.0 : 1.0);
        hp = Math.max(1.0, hp);
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
        if (isAdventure(name) || !instanceWorlds.contains(name) || finishing.contains(name) || roundTransitioning.contains(name)
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
                configureSkeletonMob(mob, mobId);
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
        if (isAdventure(name) || !instanceWorlds.contains(name) || finishing.contains(name) || !roundTransitioning.add(name)) return;
        int next = rounds.getOrDefault(name, 1) + 1;
        if (next > 3) return;
        String title = next == 2 ? hex("#9640FC") + "" + ChatColor.BOLD + "OLEADA 2"
                : hex("#FF3333") + "" + ChatColor.BOLD + "¡JEFE FINAL!";
        String heading = ChatColor.WHITE + "☠ " + title + ChatColor.WHITE + " ☠";
        for (Player p : fighters(world))
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.3f);
        new org.bukkit.scheduler.BukkitRunnable() {
            int remaining = 5;
            @Override public void run() {
                if (!instanceWorlds.contains(name) || finishing.contains(name)
                        || !roundTransitioning.contains(name) || fighters(world).isEmpty()) {
                    cancel(); return;
                }
                String subtitle = ChatColor.WHITE + (next == 2 ? "Comienza en " : "Aparece en ") + remaining + "...";
                for (Player p : fighters(world)) p.sendTitle(heading, subtitle, 0, 25, 0);
                if (remaining-- == 0) cancel();
            }
        }.runTaskTimer(this, 0L, 20L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!instanceWorlds.contains(name) || finishing.contains(name) || fighters(world).isEmpty()) {
                roundTransitioning.remove(name); return;
            }
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
        Location spawn = adventureBossStarted.contains(world.getName())
                ? adventureBossCenters.getOrDefault(world.getName(), entryPoints.get(world.getName()))
                : entryPoints.get(world.getName());
        double radiusSquared = Math.pow(adventureBossStarted.contains(world.getName())
                ? getConfig().getDouble("adventure.boss-radius-blocks", 100.0) : dungeonRadius(world.getName()), 2);
        boolean escaped = spawn != null && horizontalDistanceSquared(spawn, mob.getLocation()) > radiusSquared;
        int stalled = stuck || escaped ? lostTicks.merge(mobId, 1, Integer::sum) : 0;
        if (!stuck && !escaped) lostTicks.remove(mobId);
        if (escaped || stalled >= 4) {
            Location destination = nearbySafeLocation(world, closest.getLocation(), 2, 4);
            if (destination != null && (spawn == null || horizontalDistanceSquared(spawn, destination) <= radiusSquared)) {
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

    private void stalkFarthestEnemy(World world, List<Player> players) {
        if (players.isEmpty()) return;
        Set<UUID> candidates = new HashSet<>(enemies.getOrDefault(world.getName(), Set.of()));
        if (getConfig().getBoolean("enemy-stalking.include-bosses", true))
            candidates.addAll(bosses.getOrDefault(world.getName(), Set.of()));
        // El Dragón Rojo tiene controlador propio de vuelo: nunca debe ser acechado
        // ni teletransportado detrás de los jugadores, aunque include-bosses=true.
        if (isNetherAdventure(world.getName()))
            candidates.removeAll(bosses.getOrDefault(world.getName(), Set.of()));
        LivingEntity farthest = null;
        Player closestPlayer = null;
        double bestDistance = -1;
        for (UUID id : candidates) {
            Entity entity = Bukkit.getEntity(id);
            if (!(entity instanceof LivingEntity mob) || mob.isDead() || !mob.getWorld().equals(world)) continue;
            Player nearest = null;
            double minDistance = Double.MAX_VALUE;
            for (Player player : players) {
                double distance = mob.getLocation().distanceSquared(player.getLocation());
                if (distance < minDistance) { minDistance = distance; nearest = player; }
            }
            if (nearest != null && minDistance > bestDistance) {
                bestDistance = minDistance; farthest = mob; closestPlayer = nearest;
            }
        }
        if (farthest == null || closestPlayer == null) return;
        Location playerLocation = closestPlayer.getLocation();
        org.bukkit.util.Vector behind = playerLocation.getDirection().setY(0);
        if (behind.lengthSquared() < 0.001) return;
        behind.normalize().multiply(-getConfig().getDouble("enemy-stalking.distance-behind-player", 2.0));
        Location target = playerLocation.clone().add(behind);
        int x = target.getBlockX(), z = target.getBlockZ();
        for (int y = target.getBlockY() + 1; y >= target.getBlockY() - 2; y--) {
            if (isSafe(world, x, y, z)) {
                Location safe = new Location(world, x + 0.5, y, z + 0.5);
                safe.setDirection(playerLocation.toVector().subtract(safe.toVector()));
                farthest.teleport(safe);
                return;
            }
        }
    }

    private void startTracker(World world) {
        String name = world.getName();
        trackers.put(name, Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!instanceWorlds.contains(name) || finishing.contains(name)) return;
            Set<UUID> active = enemies.get(name);
            if (active == null) return;
            List<Player> players = fighters(world);
            if (players.isEmpty()) return;
            int elapsed = stalkTicks.merge(name, 1, Integer::sum);
            if (elapsed >= Math.max(1, getConfig().getInt("enemy-stalking.interval-seconds", 10))) {
                stalkTicks.put(name, 0);
                if (getConfig().getBoolean("enemy-stalking.enabled", true)) stalkFarthestEnemy(world, players);
            }
            for (UUID mobId : new ArrayList<>(active)) {
                Entity entity = Bukkit.getEntity(mobId);
                if (!(entity instanceof LivingEntity mob) || mob.isDead()) {
                    active.remove(mobId); protectedMobs.remove(mobId);
                    lostTicks.remove(mobId); lastMobPositions.remove(mobId); enemyTypes.remove(mobId);
                    adventureOutsideSince.remove(mobId);
                    defeated.merge(name, 1, Integer::sum);
                    continue;
                }
                if (elapsed % 2 == 0) updateEnemyName(mob, enemyTypes.getOrDefault(mobId, "SkeletalMinion"));
                if (isNetherAdventure(name)) forceNetherPiglinAggro(mob, players);
                // En EndlessXL, devolver al enemigo que abandono su zona activa
                // antes de tres segundos, sin esperar al acecho general del plugin.
                if (isAdventure(name) && adventureActiveZone.containsKey(name)) {
                    int zoneIndex = adventureActiveZone.get(name);
                    List<Encounter> zones = adventureZones(name);
                    if (zoneIndex >= 0 && zoneIndex < zones.size()) {
                        Location center = zones.get(zoneIndex).center();
                        double limit = Math.max(2.0, getConfig().getDouble(adventureConfig(name, "encounter-boundary-blocks"), 15));
                        if (horizontalDistanceSquared(center, mob.getLocation()) > limit * limit) {
                            long now = System.currentTimeMillis();
                            long first = adventureOutsideSince.computeIfAbsent(mobId, k -> now);
                            long wait = Math.min(2500L, Math.max(0L,
                                    getConfig().getLong(adventureConfig(name, "encounter.mob-return-delay-ticks"), 20L) * 50L));
                            if (now - first >= wait) {
                                Location safe = nearbySafeLocation(world, center, 0, 4);
                                if (safe == null && isSafe(world, center.getBlockX(), center.getBlockY(), center.getBlockZ()))
                                    safe = center.clone();
                                if (safe != null && mob.teleport(safe)) {
                                    adventureOutsideSince.remove(mobId);
                                    lastMobPositions.put(mobId, safe.clone());
                                    lostTicks.remove(mobId);
                                    Player nearest = players.stream().min(Comparator.comparingDouble(
                                            pl -> pl.getLocation().distanceSquared(mob.getLocation()))).orElse(null);
                                    if (nearest != null && mob instanceof org.bukkit.entity.Mob ai) ai.setTarget(nearest);
                                }
                            }
                            // No usar el sistema global de acecho para este mob fuera de su zona.
                            continue;
                        } else adventureOutsideSince.remove(mobId);
                    }
                }
                pursueEnemy(mob, mobId, players, world);
            }
            for (UUID bossId : new HashSet<>(bosses.getOrDefault(name, Set.of()))) {
                Entity entity = Bukkit.getEntity(bossId);
                if (entity instanceof LivingEntity boss && !boss.isDead()) {
                    updateEnemyName(boss, enemyTypes.getOrDefault(bossId, "SkeletonKing"));
                    if (!isNetherAdventure(name)) pursueEnemy(boss, bossId, players, world);
                }
            }
            if (isAdventure(name)) {
                if (adventureActiveZone.containsKey(name) && active.isEmpty()) completeAdventureEncounter(world);
                return;
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
                LivingEntity boss = spawnEpicBoss("SkeletonKing", loc,
                        getConfig().getDouble("skeleton-mobs.king.health", getConfig().getDouble("adventure.boss-health", 500)),
                        fighters(world).isEmpty() ? null : fighters(world).get(0));
                configureSkeletonMob(boss, "SkeletonKing");
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

    @EventHandler(priority = EventPriority.HIGHEST) public void onMobDeath(EntityDeathEvent event) {
        String name = event.getEntity().getWorld().getName();
        UUID dead = event.getEntity().getUniqueId();
        String deadType = enemyTypes.get(dead);
        if (deadType == null && bosses.getOrDefault(name, Set.of()).contains(dead)) deadType = isNetherAdventure(name)
                ? getConfig().getString("netherybreis.dragon.mythic-id", "BastionRedDragon")
                : isLabyrinth(name) ? getConfig().getString("labyrinth.mobs.king.mythic-id", "BastionSlimeKing") : "SkeletonKing";
        if (deadType != null) {
            if (getConfig().getBoolean("mob-drops.custom-loot-only", true)) {
                event.getDrops().clear();
                event.setDroppedExp(0);
            }
            rollLoot(event, deadType);
        }
        removeMobHologram(dead);
        org.bukkit.scheduler.BukkitTask bossTask = slimeBossTasks.remove(dead);
        if (bossTask != null) bossTask.cancel();
        slimeBossJumping.remove(dead);
        if (isNetherAdventure(name) && bosses.getOrDefault(name, Set.of()).contains(dead)) {
            org.bukkit.scheduler.BukkitTask task = netherDragonTasks.remove(name);
            if (task != null) task.cancel();
            stopNetherDragonDisplays(name);
            startNetherDragonDeathAnimation(name, event.getEntity());
        }
        protectedMobs.remove(dead); lostTicks.remove(dead); lastMobPositions.remove(dead); enemyTypes.remove(dead);
        kingLanding.remove(dead); kingLastLaunch.remove(dead);
        Set<UUID> active = enemies.get(name);
        if (active == null) return;
        boolean removedEnemy = active.remove(dead);
        if (removedEnemy && isAdventure(name)) {
            if (active.isEmpty()) completeAdventureEncounter(event.getEntity().getWorld());
        } else if (removedEnemy) {
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
            List<Player> winners = onlineParticipants(name);
            releaseParty(name);
            for (Player p : winners) {
                frozen.remove(p.getUniqueId());
                p.sendTitle(ChatColor.GREEN + "" + ChatColor.BOLD + "✦ ¡MAZMORRA COMPLETADA! ✦",
                        ChatColor.WHITE + "¡Has derrotado a todos los enemigos!", 10, 80, 10);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                partyMessage(p, ChatColor.GOLD + "¡VICTORIA!");
                p.sendMessage(ChatColor.WHITE + "Tienes " + ChatColor.GOLD + "10 segundos" + ChatColor.WHITE
                        + " para recoger tus recompensas.");
                p.sendMessage(ChatColor.GRAY + "Después regresarás automáticamente al spawn.");
            }
            new org.bukkit.scheduler.BukkitRunnable() {
                int seconds = 10;
                @Override public void run() {
                    if (!instanceWorlds.contains(name)) { cancel(); return; }
                    for (Player p : winners) {
                        if (p.isOnline() && name.equals(playingWorld.get(p.getUniqueId()))) {
                            p.sendActionBar(ChatColor.GOLD + "✦ RECOGE TUS RECOMPENSAS ✦ "
                                    + ChatColor.WHITE + "Regresando al spawn en " + seconds + "s...");
                        }
                    }
                    if (--seconds < 0) cancel();
                }
            }.runTaskTimer(this, 0L, 20L);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                returnAllParticipants(name);
            }, 200L);
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        adminBreakMode.remove(e.getPlayer().getUniqueId());
        parkourReturning.remove(e.getPlayer().getUniqueId());
        adminFreeMode.remove(e.getPlayer().getUniqueId());
        VoteSession session = voterSessions.get(e.getPlayer().getUniqueId());
        if (session != null) cancelVote(session, "Votación cancelada: un jugador se desconectó.");
        clearLobbyEffects(e.getPlayer());
        awaitingDungeonRespawn.remove(e.getPlayer().getUniqueId());
        // Limpiar la sesion al desconectar, para que no quede bloqueado "ya esta en partida".
        String name = playingWorld.get(e.getPlayer().getUniqueId());
        if (name != null && finishing.contains(name)) returnPlayer(e.getPlayer(), name);
        else if (name != null || preparing.contains(e.getPlayer().getUniqueId())) leave(e.getPlayer());
    }
}
