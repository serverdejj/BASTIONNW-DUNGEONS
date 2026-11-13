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

    @Override public void onEnable() {
        getDataFolder().mkdirs();
        new File(getDataFolder(), "schematics").mkdirs();
        Objects.requireNonNull(getCommand("dungeon")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Bastion Dungeons 1.2.3-dev listo.");
    }

    private UUID leader(UUID id) { return leaderOf.getOrDefault(id, id); }
    private List<Player> members(UUID leader) {
        List<Player> result = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (leader(p.getUniqueId()).equals(leader)) result.add(p);
        return result;
    }
    private void msg(Player p, String text) { p.sendMessage(ChatColor.GOLD + "[BASTION] " + ChatColor.WHITE + text); }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Solo jugadores."); return true; }
        UUID id = p.getUniqueId();
        if (args.length == 0) { msg(p,"/dungeon party add <jugador> | accept | leave | list | /dungeon go | /dungeon leave"); return true; }
        if (args[0].equalsIgnoreCase("party")) {
            if (args.length < 2) { msg(p,"Usa add, accept, leave o list."); return true; }
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "add" -> {
                    if (args.length < 3) { msg(p,"/dungeon party add <jugador>"); return true; }
                    if (!leader(id).equals(id)) { msg(p,"Solo el lider invita."); return true; }
                    if (members(id).size() >= 4) { msg(p,"Party llena (maximo 4)."); return true; }
                    Player target = Bukkit.getPlayerExact(args[2]);
                    if (target == null || target.equals(p)) { msg(p,"Jugador no disponible."); return true; }
                    if (leaderOf.containsKey(target.getUniqueId()) || playingWorld.containsKey(target.getUniqueId())) { msg(p,"Ese jugador ya esta en una party o partida."); return true; }
                    invites.put(target.getUniqueId(), id);
                    msg(target,p.getName()+" te invito. Usa /dungeon party accept"); msg(p,"Invitacion enviada.");
                }
                case "accept" -> {
                    UUID owner = invites.remove(id);
                    if (owner == null || Bukkit.getPlayer(owner) == null || playingWorld.containsKey(owner) || members(owner).size() >= 4) { msg(p,"No hay invitacion valida."); return true; }
                    if (leaderOf.containsKey(id) || playingWorld.containsKey(id)) { msg(p,"Ya estas en otra party o partida."); return true; }
                    leaderOf.put(id, owner); msg(p,"Te uniste a la party.");
                    Player lead = Bukkit.getPlayer(owner); if (lead != null) msg(lead,p.getName()+" se unio.");
                }
                case "leave" -> {
                    if (playingWorld.containsKey(id)) { msg(p,"En partida usa /dungeon leave."); return true; }
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
            if (!leader(id).equals(id)) { msg(p,"Solo el lider puede iniciar."); return true; }
            List<Player> team = members(id);
            if (team.stream().anyMatch(q -> playingWorld.containsKey(q.getUniqueId()))) { msg(p,"Alguien ya esta en partida."); return true; }
            File folder = new File(getDataFolder(), "schematics");
            File[] schems = folder.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".schem"));
            if (schems == null || schems.length == 0) { msg(p,"Pon archivos .schem en plugins/BastionDungeons/schematics/"); return true; }
            File chosen = schems[new Random().nextInt(schems.length)];
            try { start(team, chosen); } catch (Exception ex) { getLogger().log(java.util.logging.Level.SEVERE,"Error creando dungeon",ex); msg(p,"No se pudo iniciar. Revisa consola."); }
            return true;
        }
        msg(p,"Comando desconocido."); return true;
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
            for (Player member : team) msg(member, "Preparando mazmorra (" + chunkCount + " chunks)...");
            CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).whenComplete((ignored, failure) ->
                Bukkit.getScheduler().runTask(this, () -> {
                    if (!instanceWorlds.contains(name)) return;
                    if (failure != null) {
                        getLogger().log(java.util.logging.Level.SEVERE, "Error cargando chunks", failure);
                        cleanupFailedWorld(name);
                        for (Player member : team) if (member.isOnline()) msg(member, "No se pudo preparar la mazmorra.");
                        return;
                    }
                    try {
                        try (EditSession edit = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                            Operations.complete(new ClipboardHolder(clipboard).createPaste(edit)
                                    .to(pasteAt).ignoreAirBlocks(false).build());
                        }
                        Location spawn = safeRandomLocation(world, clipboard, pasteAt);
                        if (spawn == null) throw new IllegalStateException("El schematic no tiene suelo seguro para iniciar");
                        world.setSpawnLocation(spawn);
                        for (Player member : team) {
                            if (!member.isOnline()) continue;
                            returns.put(member.getUniqueId(), member.getLocation().clone());
                            playingWorld.put(member.getUniqueId(), name);
                            member.teleport(spawn);
                            frozen.add(member.getUniqueId());
                            msg(member, "Mazmorra: " + file.getName() + ". Preparados para el combate.");
                        }
                        teamSizes.put(name, team.size());
                        enemies.put(name, new HashSet<>());
                        entryPoints.put(name, spawn.clone());
                        startCountdown(world, clipboard, pasteAt, team.size());
                    } catch (Exception ex) {
                        getLogger().log(java.util.logging.Level.SEVERE, "Error pegando schematic", ex);
                        cleanupFailedWorld(name);
                        for (Player member : team) if (member.isOnline()) msg(member, "Error creando mazmorra; revisa consola.");
                    }
                }));
        } catch (Exception ex) {
            cleanupFailedWorld(name);
            throw ex;
        }
    }

    private void cleanupFailedWorld(String name) {
        instanceWorlds.remove(name);
        World world = Bukkit.getWorld(name);
        if (world != null && world.getPlayers().isEmpty()) Bukkit.unloadWorld(world, false);
    }

    private void leave(Player p) {
        UUID id = p.getUniqueId(); frozen.remove(id); String name = playingWorld.remove(id);
        if (name == null) { msg(p,"No estas en una mazmorra."); return; }
        Location back = returns.remove(id);
        p.setGameMode(GameMode.SURVIVAL);
        if (back != null) p.teleport(back);
        if (!playingWorld.containsValue(name)) {
            World world = Bukkit.getWorld(name);
            if (world != null) Bukkit.unloadWorld(world, false);
            instanceWorlds.remove(name);
            enemies.remove(name);
            bosses.remove(name);
            bossAreas.remove(name);
            teamSizes.remove(name);
            entryPoints.remove(name);
            // Se conserva el directorio del mundo para evitar borrar archivos automaticamente.
        }
        msg(p,"Saliste de la mazmorra.");
    }
    @EventHandler public void onDeath(PlayerDeathEvent e) {
        if (playingWorld.containsKey(e.getEntity().getUniqueId())) {
            e.setKeepInventory(false);
            e.setKeepLevel(false);
            // Los drops quedan en el mundo. El modo espectador requiere el evento respawn.
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
            int remaining = 10;
            @Override public void run() {
                if (!instanceWorlds.contains(name) || world.getPlayers().isEmpty()) { cancel(); return; }
                if (remaining == 0) {
                    for (Player player : world.getPlayers()) {
                        frozen.remove(player.getUniqueId());
                        player.sendTitle(ChatColor.GREEN + "¡A LUCHAR!", ChatColor.YELLOW + "¡Derrota a los enemigos!", 0, 35, 10);
                        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.65f, 1.5f);
                    }
                    spawnWave(world, clipboard, pasteAt, partySize);
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

    private void spawnWave(World world, Clipboard clipboard, BlockVector3 pasteAt, int partySize) {
        String name = world.getName();
        if (!instanceWorlds.contains(name)) return;
        String[] types = {"SkeletalMinion", "SkeletalKnight"};
        int count = switch (partySize) { case 1 -> 5; case 2 -> 8; case 3 -> 12; default -> 16; };
        for (int i = 0; i < count; i++) {
            Location center = world.getPlayers().isEmpty() ? entryPoints.get(name) :
                    world.getPlayers().get(i % world.getPlayers().size()).getLocation();
            Location loc = nearbySafeLocation(world, center, 5, 12);
            if (loc == null) { getLogger().warning("Sin espacio seguro a 5-12 bloques en " + name); continue; }
            try {
                LivingEntity mob = spawnMythic(types[random.nextInt(types.length)], loc);
                enemies.get(name).add(mob.getUniqueId());
            } catch (Exception ex) { getLogger().warning("No se pudo invocar MythicMob: " + ex); break; }
        }
        if (enemies.get(name).isEmpty()) {
            for (Player p : world.getPlayers()) msg(p, "No se encontraron enemigos o posiciones seguras. Revisa consola.");
            return;
        }
        for (Player p : world.getPlayers()) msg(p, "Derrota a " + enemies.get(name).size() + " enemigos para invocar al jefe.");
        // Store schematic bounds for the boss spawn when the last enemy dies.
        bossAreas.put(name, new BossArea(clipboard, pasteAt));
    }

    private record BossArea(Clipboard clipboard, BlockVector3 pasteAt) {}
    private final Map<String, BossArea> bossAreas = new HashMap<>();

    @EventHandler public void onMobDeath(EntityDeathEvent event) {
        String name = event.getEntity().getWorld().getName();
        Set<UUID> remaining = enemies.get(name);
        if (remaining == null) return;
        UUID dead = event.getEntity().getUniqueId();
        protectedMobs.remove(dead);
        if (remaining.remove(dead) && remaining.isEmpty() && !bosses.containsKey(name)) {
            BossArea area = bossAreas.get(name);
            Location loc = nearbySafeLocation(event.getEntity().getWorld(),
                    entryPoints.get(name), 5, 12);
            if (loc == null) { getLogger().warning("No hay lugar para SkeletonKing en " + name); return; }
            try {
                LivingEntity boss = spawnMythic("SkeletonKing", loc);
                double hp = switch (teamSizes.getOrDefault(name, 1)) { case 1 -> 100; case 2 -> 150; case 3 -> 200; default -> 250; };
                var attribute = boss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (attribute != null) { attribute.setBaseValue(hp); boss.setHealth(hp); }
                bosses.put(name, boss.getUniqueId());
                for (Player p : boss.getWorld().getPlayers()) msg(p, "¡SkeletonKing ha aparecido!");
            } catch (Exception ex) { getLogger().warning("Error al crear SkeletonKing: " + ex); }
        } else if (dead.equals(bosses.get(name))) {
            bosses.remove(name);
            for (Player p : new ArrayList<>(event.getEntity().getWorld().getPlayers())) {
                msg(p, "¡Victoria! Has derrotado a SkeletonKing.");
                leave(p);
            }
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        // Limpiar la sesion al desconectar, para que no quede bloqueado "ya esta en partida".
        if (playingWorld.containsKey(e.getPlayer().getUniqueId())) leave(e.getPlayer());
    }
}
