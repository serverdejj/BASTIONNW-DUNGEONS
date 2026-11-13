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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.util.*;

public final class BastionDungeons extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, UUID> leaderOf = new HashMap<>();
    private final Map<UUID, UUID> invites = new HashMap<>();
    private final Map<UUID, Location> returns = new HashMap<>();
    private final Map<UUID, String> playingWorld = new HashMap<>();
    private final Set<String> instanceWorlds = new HashSet<>();

    @Override public void onEnable() {
        getDataFolder().mkdirs();
        new File(getDataFolder(), "schematics").mkdirs();
        Objects.requireNonNull(getCommand("dungeon")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Bastion Dungeons 1.1.0-dev listo (parties y schematics). Combate MythicMobs aun no implementado.");
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
            try (EditSession edit = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(world))) {
                Operations.complete(new ClipboardHolder(clipboard).createPaste(edit).to(pasteAt).ignoreAirBlocks(false).build());
            }
            // El origen del schematic debe colocarse cerca de una zona segura de inicio.
            Location spawn = new Location(world, 0.5, 102, 0.5);
            world.setSpawnLocation(spawn);
            for (Player member : team) {
                returns.put(member.getUniqueId(), member.getLocation().clone());
                playingWorld.put(member.getUniqueId(), name);
                member.teleport(spawn);
                msg(member,"Mazmorra: " + file.getName() + ". Prototipo: aun sin mobs ni boss automaticos.");
            }
        } catch (Exception ex) {
            instanceWorlds.remove(name);
            Bukkit.unloadWorld(world, false);
            throw ex;
        }
    }

    private void leave(Player p) {
        UUID id = p.getUniqueId(); String name = playingWorld.remove(id);
        if (name == null) { msg(p,"No estas en una mazmorra."); return; }
        Location back = returns.remove(id);
        p.setGameMode(GameMode.SURVIVAL);
        if (back != null) p.teleport(back);
        if (!playingWorld.containsValue(name)) {
            World world = Bukkit.getWorld(name);
            if (world != null) Bukkit.unloadWorld(world, false);
            instanceWorlds.remove(name);
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
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        // La instancia se mantiene mientras haya participantes; no se fuerza salida al desconectar.
    }
}
