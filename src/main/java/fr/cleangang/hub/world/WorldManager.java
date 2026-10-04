package fr.cleangang.hub.world;

import fr.cleangang.hub.CleanGangHub;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WorldManager implements Listener {
    public static final List<String> TYPES = List.of("void", "normal", "flat", "nether", "end");

    private final CleanGangHub plugin;

    public WorldManager(CleanGangHub plugin) { this.plugin = plugin; }

    /** Charge tous les mondes déclarés dans config.yml (s'ils existent sur le disque). */
    public void loadAll() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("worlds");
        if (sec == null) return;
        for (String name : sec.getKeys(false)) {
            World w = Bukkit.getWorld(name);
            if (w == null) {
                if (!existsOnDisk(name)) {
                    plugin.getLogger().warning("Monde '" + name + "' introuvable dans le dossier du serveur — ignoré. "
                            + "Copie le dossier de la map ou fais /cghub world create " + name);
                    continue;
                }
                w = load(name, sec.getString(name + ".type", "normal"));
            }
            if (w != null) applySettings(w);
        }
    }

    public boolean existsOnDisk(String name) {
        return new File(Bukkit.getWorldContainer(), name + File.separator + "level.dat").exists();
    }

    public World load(String name, String type) {
        WorldCreator wc = new WorldCreator(name);
        switch (type.toLowerCase(Locale.ROOT)) {
            case "void" -> wc.generator(new VoidGenerator());
            case "flat" -> wc.type(WorldType.FLAT);
            case "nether" -> wc.environment(World.Environment.NETHER);
            case "end" -> wc.environment(World.Environment.THE_END);
            default -> { /* normal */ }
        }
        World w = wc.createWorld();
        if (w != null) plugin.getLogger().info("Monde chargé : " + name + " (" + type + ")");
        return w;
    }

    /** Crée (ou charge) un monde et l'enregistre dans config.yml pour les prochains démarrages. */
    public World create(String name, String type) {
        boolean isNew = !existsOnDisk(name);
        World w = load(name, type);
        if (w == null) return null;
        if (isNew && type.equalsIgnoreCase("void")) {
            Block b = w.getBlockAt(0, 63, 0);
            if (b.getType().isAir()) b.setType(Material.BEDROCK);
            w.setSpawnLocation(0, 64, 0);
        }
        if (!plugin.getConfig().isConfigurationSection("worlds." + name)) {
            plugin.getConfig().set("worlds." + name + ".type", type.toLowerCase(Locale.ROOT));
            plugin.saveConfig();
        }
        applySettings(w);
        return w;
    }

    public boolean unload(String name) {
        World w = Bukkit.getWorld(name);
        if (w == null || w.equals(hubWorld())) return false;
        Location safe = hubWorld().getSpawnLocation();
        for (Player p : new ArrayList<>(w.getPlayers())) p.teleport(safe);
        return Bukkit.unloadWorld(w, true);
    }

    @SuppressWarnings("deprecation")
    private void applySettings(World w) {
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("worlds." + w.getName());
        if (s == null) return;
        if (s.contains("pvp")) w.setPVP(s.getBoolean("pvp"));
    }

    public World hubWorld() {
        World w = Bukkit.getWorld(plugin.getConfig().getString("hub-world", "world"));
        return w != null ? w : Bukkit.getWorlds().get(0);
    }

    public List<String> knownWorlds() {
        List<String> out = new ArrayList<>();
        for (World w : Bukkit.getWorlds()) out.add(w.getName());
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("worlds");
        if (sec != null) for (String k : sec.getKeys(false)) if (!out.contains(k)) out.add(k);
        return out;
    }

    /** Mode de jeu forcé par monde. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("cghub.bypass")) return;
        String gm = plugin.getConfig().getString("worlds." + p.getWorld().getName() + ".gamemode");
        if (gm == null) return;
        try {
            p.setGameMode(GameMode.valueOf(gm.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("gamemode invalide pour " + p.getWorld().getName() + " : " + gm);
        }
    }
}
