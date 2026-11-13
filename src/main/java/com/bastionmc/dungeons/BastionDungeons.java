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
    private final Map<String, UUID> bosses = new HashMap<>();
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
    private static final int TOTAL_MOBS = 10;
    private static final int MAX_ACTIVE = 3;
    private static final int PASTE_Y = 100;
    private final File spawnFile = new File(getDataFolder(), "spawns.yml");

    @Override public void onEnable() {
        getDataFolder().mkdirs();
        new File(getDataFolder(), "schematics").mkdirs();
        Objects.requireNonNull(getCommand("dungeon")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        loadSchedule();
        Bukkit.getScheduler().runTaskTimer(this, this::checkSchedule, 20L, 20L);
        getLogger().info("Bastion Dungeons 1.2.7-dev listo.");
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

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Solo jugadores."); return true; }
        UUID id = p.getUniqueId();
        if (args.length == 0) { msg(p,"/dungeon party add <jugador> | accept | leave | list | /dungeon go | /dungeon leave | /dungeon setspawn"); return true; }
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
        for (UUID mob : enemies.getOrDefault(name, Set.of())) { protectedMobs.remove(mob); lostTicks.remove(mob); }
        UUID boss = bosses.remove(name);
        if (boss != null) protectedMobs.remove(boss);
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
        if (!frozen.contains(event.getPlayer().getUniqueId()) || event.getTo() == null) return;
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
                    totalSpawned.put(name, 0); defeated.put(name, 0);
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

    private void spawnNextMobs(World world) {
        String name = world.getName();
        if (!instanceWorlds.contains(name) || bossCountdownStarted.containsKey(name)) return;
        Set<UUID> active = enemies.get(name);
        if (active == null) return;
        List<Player> players = fighters(world);
        if (players.isEmpty()) return;
        int attempts = 0;
        while (active.size() < MAX_ACTIVE && totalSpawned.getOrDefault(name, 0) < TOTAL_MOBS && attempts++ < 25) {
            Player target = players.get(random.nextInt(players.size()));
            Location loc = nearbySafeLocation(world, target.getLocation(), 5, 8);
            if (loc == null) continue;
            try {
                String mobId = random.nextBoolean() ? "SkeletalMinion" : "SkeletalKnight";
                LivingEntity mob = spawnMythic(mobId, loc);
                active.add(mob.getUniqueId());
                totalSpawned.merge(name, 1, Integer::sum);
            } catch (Exception ex) { getLogger().log(java.util.logging.Level.WARNING, "Error invocando enemigo", ex); break; }
        }
        if (active.isEmpty() && totalSpawned.getOrDefault(name, 0) < TOTAL_MOBS && announcedMissing.add(name)) {
            getLogger().warning("Sin posiciones seguras para enemigos cerca de jugadores en " + name);
            for (Player p : players) msg(p, "No se encontraron posiciones seguras para enemigos.");
        }
        if (!active.isEmpty()) announcedMissing.remove(name);
    }

    private void startTracker(World world) {
        String name = world.getName();
        trackers.put(name, Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!instanceWorlds.contains(name) || bossCountdownStarted.containsKey(name)) return;
            Set<UUID> active = enemies.get(name);
            if (active == null) return;
            List<Player> players = fighters(world);
            if (players.isEmpty()) return;
            for (UUID mobId : new ArrayList<>(active)) {
                Entity entity = Bukkit.getEntity(mobId);
                if (!(entity instanceof LivingEntity mob) || mob.isDead()) {
                    // Un enemigo desaparecido no debe bloquear la partida.
                    active.remove(mobId); protectedMobs.remove(mobId); lostTicks.remove(mobId);
                    defeated.merge(name, 1, Integer::sum);
                    continue;
                }
                Player closest = players.stream().min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(mob.getLocation()))).orElse(null);
                if (closest == null) continue;
                if (mob.getLocation().distanceSquared(closest.getLocation()) > 20 * 20) {
                    int ticks = lostTicks.merge(mobId, 1, Integer::sum);
                    if (ticks >= 2) {
                        Location loc = nearbySafeLocation(world, closest.getLocation(), 5, 8);
                        if (loc != null) mob.teleport(loc);
                        lostTicks.remove(mobId);
                    }
                } else lostTicks.remove(mobId);
            }
            if (defeated.getOrDefault(name, 0) >= TOTAL_MOBS && active.isEmpty()) startBossCountdown(world);
            else spawnNextMobs(world);
        }, 40L, 100L));
        for (Player p : world.getPlayers()) msg(p, "¡Derrota a 10 enemigos! Apareceran de 3 en 3.");
    }

    private void startBossCountdown(World world) {
        String name = world.getName();
        if (bossCountdownStarted.putIfAbsent(name, true) != null) return;
        new org.bukkit.scheduler.BukkitRunnable() {
            int seconds = 5;
            @Override public void run() {
                if (!instanceWorlds.contains(name) || fighters(world).isEmpty()) { cancel(); return; }
                if (seconds == 0) {
                    for (Player p : world.getPlayers()) {
                        frozen.remove(p.getUniqueId());
                        p.sendTitle(ChatColor.DARK_RED + "¡SKELETON KING!", ChatColor.GOLD + "¡DERROTA AL JEFE!", 0, 45, 10);
                        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, .7f);
                    }
                    spawnBoss(world); cancel(); return;
                }
                for (Player p : world.getPlayers()) {
                    p.sendTitle(ChatColor.RED + "" + seconds, ChatColor.GOLD + "¡EL JEFE SE ACERCA!", 0, 23, 0);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, seconds <= 3 ? 1.8f : 1f);
                }
                seconds--;
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void spawnBoss(World world) {
        String name = world.getName();
        List<Player> players = fighters(world);
        if (players.isEmpty()) return;
        Location loc = nearbySafeLocation(world, players.get(0).getLocation(), 5, 8);
        if (loc == null) {
            getLogger().warning("No hay lugar para SkeletonKing en " + name);
            for (Player p : players) msg(p, "No hay lugar para el jefe. Revisa consola.");
            return;
        }
        try {
            LivingEntity boss = spawnMythic("SkeletonKing", loc);
            double hp = switch (teamSizes.getOrDefault(name, 1)) { case 1 -> 100; case 2 -> 150; case 3 -> 200; default -> 250; };
            var attribute = boss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (attribute != null) { attribute.setBaseValue(hp); boss.setHealth(hp); }
            bosses.put(name, boss.getUniqueId());
            for (Player p : world.getPlayers()) p.sendMessage(hex("#FF0000") + "" + ChatColor.BOLD + "JEFE ESQUELETO " + ChatColor.DARK_GRAY + "» " + ChatColor.WHITE + "¡Ha aparecido el " + hex("#FD9696") + "Jefe Final" + ChatColor.WHITE + "!.");
        } catch (Exception ex) { getLogger().log(java.util.logging.Level.WARNING, "Error al crear SkeletonKing", ex); }
    }

    @EventHandler public void onMobDeath(EntityDeathEvent event) {
        String name = event.getEntity().getWorld().getName();
        UUID dead = event.getEntity().getUniqueId();
        protectedMobs.remove(dead); lostTicks.remove(dead);
        Set<UUID> active = enemies.get(name);
        if (active == null) return;
        if (active.remove(dead)) {
            int kills = defeated.merge(name, 1, Integer::sum);
            for (Player p : event.getEntity().getWorld().getPlayers())
                p.sendMessage(net.md_5.bungee.api.ChatColor.of("#FFA5A5") + "¡Uno menos!" + org.bukkit.ChatColor.WHITE
                        + ", ¡Quedan " + Math.max(0, TOTAL_MOBS - kills) + "/" + TOTAL_MOBS + " enemigos!.");
            if (kills >= TOTAL_MOBS && active.isEmpty()) startBossCountdown(event.getEntity().getWorld());
            else spawnNextMobs(event.getEntity().getWorld());
        } else if (dead.equals(bosses.get(name))) {
            bosses.remove(name);
            if (!finishing.add(name)) return;
            World world = event.getEntity().getWorld();
            List<Player> winners = new ArrayList<>(world.getPlayers());
            releaseParty(name);
            for (Player p : winners) {
                frozen.add(p.getUniqueId());
                p.sendTitle(net.md_5.bungee.api.ChatColor.of("#FFF800") + "" + ChatColor.BOLD + "¡VICTORIA!",
                        ChatColor.WHITE + "¡Han derrotado a todos los enemigos!", 10, 80, 10);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                msg(p, "¡Victoria! Regresando al lobby en 5 segundos...");
            }
            Bukkit.getScheduler().runTaskLater(this, () -> {
                for (Player p : winners) {
                    if (name.equals(playingWorld.get(p.getUniqueId()))) returnPlayer(p, name);
                }
                finishInstance(name);
            }, 100L);
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
