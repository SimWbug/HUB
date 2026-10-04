package fr.cleangang.hub.world.listener;

import fr.cleangang.hub.CleanGangHub;
import fr.cleangang.hub.world.BackStore;
import fr.cleangang.hub.world.WorldEntry;
import fr.cleangang.hub.world.WorldFlag;
import fr.cleangang.hub.world.WorldService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/** Mode de jeu forcé, whitelist de monde, /world back et liens de portails. */
public final class PlayerWorldListener implements Listener {
    private final CleanGangHub plugin;
    private final WorldService worlds;
    private final BackStore back;

    public PlayerWorldListener(CleanGangHub plugin) {
        this.plugin = plugin;
        this.worlds = plugin.worlds();
        this.back = plugin.back();
    }

    // ------------------------------------------------------------ whitelist + back

    public boolean canEnter(Player p, World w) {
        WorldEntry e = worlds.entryOf(w);
        if (e == null || !worlds.bool(e, WorldFlag.WHITELIST)) return true;
        return p.hasPermission("cghub.bypass.whitelist") || e.whitelist.contains(p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo().getWorld() == null || e.getTo().getWorld().equals(e.getFrom().getWorld())) return;
        if (!canEnter(e.getPlayer(), e.getTo().getWorld())) {
            e.setCancelled(true);
            plugin.msg(e.getPlayer(), "<red>Tu n'es pas sur la whitelist du monde <white>"
                    + worlds.label(e.getTo().getWorld()) + "<red>.");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleportDone(PlayerTeleportEvent e) {
        if (e.getTo().getWorld() != null && !e.getTo().getWorld().equals(e.getFrom().getWorld())) {
            back.put(e.getPlayer().getUniqueId(), e.getFrom());
        }
    }

    // ------------------------------------------------------------ mode de jeu

    public void applyGameMode(Player p) {
        WorldEntry e = worlds.entryOf(p.getWorld());
        if (e == null) return;
        String force = worlds.flag(e, WorldFlag.FORCE_GAME_MODE);
        boolean apply = force.equals("true")
                || (force.equals("false-with-permission") && !p.hasPermission("cghub.bypass.gamemode"));
        if (!apply) return;
        try {
            GameMode gm = GameMode.valueOf(worlds.flag(e, WorldFlag.GAME_MODE));
            if (p.getGameMode() != gm) p.setGameMode(gm);
        } catch (IllegalArgumentException ignored) { }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent e) { applyGameMode(e.getPlayer()); }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (!canEnter(p, p.getWorld())) {
                p.teleport(worlds.hubWorld().getSpawnLocation());
                plugin.msg(p, "<red>Tu n'es plus sur la whitelist de ce monde, retour au hub.");
            }
            applyGameMode(p);
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { back.save(); }

    // ------------------------------------------------------------ portails

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent e) {
        boolean nether = e.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL;
        boolean end = e.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL;
        if (!nether && !end) return;
        Location to = route(e.getFrom(), e.getTo(), nether);
        if (to == null) {
            e.setCancelled(true);
            plugin.msg(e.getPlayer(), "<red>Ce portail est désactivé dans ce monde.");
            return;
        }
        e.setTo(to);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) {
        boolean nether = e.getPortalType() == PortalType.NETHER;
        boolean end = e.getPortalType() == PortalType.ENDER;
        if (!nether && !end) return;
        Location to = route(e.getFrom(), e.getTo(), nether);
        if (to == null) e.setCancelled(true);
        else e.setTo(to);
    }

    /** Destination selon les liens du monde de départ ; null = portail bloqué. */
    private Location route(Location from, Location vanillaTo, boolean nether) {
        World fw = from.getWorld();
        WorldEntry e = worlds.entryOf(fw);
        if (e == null) return vanillaTo;
        if (!worlds.bool(e, nether ? WorldFlag.NETHER_PORTAL : WorldFlag.END_PORTAL)) return null;
        if (!plugin.getConfig().getBoolean("world-link-enabled", true)) return vanillaTo;

        WorldFlag link;
        if (nether) link = fw.getEnvironment() == World.Environment.NETHER ? WorldFlag.NORMAL_WORLD : WorldFlag.NETHER_WORLD;
        else link = fw.getEnvironment() == World.Environment.THE_END ? WorldFlag.NORMAL_WORLD : WorldFlag.END_WORLD;
        World target = worlds.resolve(worlds.flag(e, link));
        if (target == null) return null;
        if (vanillaTo != null && target.equals(vanillaTo.getWorld())) return vanillaTo;

        if (nether) {
            double scale = fw.getCoordinateScale() / target.getCoordinateScale();
            double y = Math.max(target.getMinHeight() + 1, Math.min(target.getMaxHeight() - 2, from.getY()));
            return new Location(target, from.getX() * scale, y, from.getZ() * scale, from.getYaw(), from.getPitch());
        }
        if (target.getEnvironment() == World.Environment.THE_END) {
            // plateforme d'obsidienne comme en vanilla
            for (int x = 98; x <= 102; x++) for (int z = -2; z <= 2; z++) {
                target.getBlockAt(x, 48, z).setType(Material.OBSIDIAN);
                for (int y = 49; y <= 51; y++) target.getBlockAt(x, y, z).setType(Material.AIR);
            }
            return new Location(target, 100.5, 49, 0.5, 90f, 0f);
        }
        return target.getSpawnLocation();
    }
}
