package fr.cleangang.hub.world;

import fr.cleangang.hub.CleanGangHub;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Gestion des mondes pour Paper 26.x.
 * Chaque monde est identifié par une clé (ex. cleangang:poulet) et stocké dans
 * world/dimensions/<namespace>/<clé>/. Les anciens dossiers à la racine du serveur
 * sont chargés via le constructeur par nom, ce qui déclenche la conversion de Paper.
 */
public final class WorldManager implements Listener {
    public static final String NAMESPACE = "cleangang";
    public static final List<String> TYPES = List.of("void", "normal", "flat", "nether", "end");
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_.\\-]+");

    private final CleanGangHub plugin;

    public WorldManager(CleanGangHub plugin) { this.plugin = plugin; }

    public static boolean isValidId(String id) { return id != null && VALID_ID.matcher(id).matches(); }

    // ------------------------------------------------------------------ clés & résolution

    /** Clé du monde pour un id de config : worlds.<id>.key, sinon cleangang:<id>. */
    public NamespacedKey keyFor(String id) {
        String k = plugin.getConfig().getString("worlds." + id + ".key");
        NamespacedKey nk = k == null ? null : NamespacedKey.fromString(k);
        return nk != null ? nk : new NamespacedKey(NAMESPACE, id.toLowerCase(Locale.ROOT));
    }

    /** Accepte un id de config, une clé "ns:clé" ou un ancien nom de monde. */
    public World resolve(String ref) {
        if (ref == null || ref.isBlank()) return null;
        if (ref.indexOf(':') >= 0) {
            NamespacedKey k = NamespacedKey.fromString(ref);
            return k == null ? null : Bukkit.getWorld(k);
        }
        if (plugin.getConfig().isConfigurationSection("worlds." + ref)) {
            World w = Bukkit.getWorld(keyFor(ref));
            if (w != null) return w;
        }
        return Bukkit.getWorld(ref);
    }

    /** Id de config correspondant à ce monde, ou null. */
    public String idOf(World w) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("worlds");
        if (sec == null) return null;
        for (String id : sec.getKeys(false)) if (keyFor(id).equals(w.getKey())) return id;
        return null;
    }

    /** Nom lisible : id de config si connu, sinon la clé. */
    public String label(World w) {
        String id = idOf(w);
        return id != null ? id : w.getKey().asString();
    }

    // ------------------------------------------------------------------ disque

    private File levelFolder() {
        String level = "world";
        File props = new File("server.properties");
        if (props.isFile()) {
            try (InputStream in = new FileInputStream(props)) {
                Properties p = new Properties();
                p.load(in);
                level = p.getProperty("level-name", "world");
            } catch (IOException ignored) { }
        }
        return new File(Bukkit.getWorldContainer(), level);
    }

    public boolean existsAsDimension(NamespacedKey k) {
        File dir = new File(levelFolder(), "dimensions" + File.separator + k.getNamespace() + File.separator + k.getKey());
        return dir.isDirectory();
    }

    /** Ancien format : dossier avec level.dat à la racine du serveur (à convertir). */
    public boolean existsAsLegacyFolder(String id) {
        return new File(Bukkit.getWorldContainer(), id + File.separator + "level.dat").isFile();
    }

    public boolean existsOnDisk(String id) {
        return existsAsDimension(keyFor(id)) || existsAsLegacyFolder(id);
    }

    // ------------------------------------------------------------------ chargement

    /** Charge tous les mondes déclarés dans config.yml. */
    public void loadAll() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("worlds");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            if (resolve(id) != null) continue;
            World w = load(id, sec.getString(id + ".type", "normal"), false);
            if (w == null) {
                plugin.getLogger().warning("Monde '" + id + "' introuvable (ni " + keyFor(id).asString()
                        + " ni dossier '" + id + "' à la racine) — ignoré. Fais /cghub world create " + id);
            }
        }
    }

    /**
     * Charge un monde : d'abord au nouveau format (clé), sinon un ancien dossier à convertir,
     * sinon le crée si allowCreate.
     */
    public World load(String id, String type, boolean allowCreate) {
        World already = resolve(id);
        if (already != null) return already;

        NamespacedKey key = keyFor(id);
        WorldCreator wc;
        if (existsAsDimension(key)) {
            wc = WorldCreator.ofKey(key);
        } else if (existsAsLegacyFolder(id)) {
            plugin.getLogger().info("Ancien monde '" + id + "' détecté : conversion au format 26.x par Paper…");
            wc = new WorldCreator(id); // chemin officiel pour les mondes non convertis
        } else if (allowCreate) {
            wc = WorldCreator.ofKey(key);
        } else {
            return null;
        }

        switch (type.toLowerCase(Locale.ROOT)) {
            case "void" -> wc.generator(new VoidGenerator());
            case "flat" -> wc.type(WorldType.FLAT);
            case "nether" -> wc.environment(World.Environment.NETHER);
            case "end" -> wc.environment(World.Environment.THE_END);
            default -> { /* normal */ }
        }

        World w = wc.createWorld();
        if (w != null) {
            remember(id, w, type);
            plugin.getLogger().info("Monde chargé : " + id + " -> " + w.getKey().asString() + " (" + type + ")");
        }
        return w;
    }

    /** Crée (ou charge) un monde et l'enregistre dans config.yml. */
    public World create(String id, String type) {
        boolean isNew = !existsOnDisk(id);
        World w = load(id, type, true);
        if (w == null) return null;
        if (isNew && type.equalsIgnoreCase("void")) {
            Block b = w.getBlockAt(0, 63, 0);
            if (b.getType().isAir()) b.setType(Material.BEDROCK);
            w.setSpawnLocation(0, 64, 0);
        }
        return w;
    }

    /** Note le type et la vraie clé du monde (utile après conversion d'un ancien dossier). */
    private void remember(String id, World w, String type) {
        String base = "worlds." + id;
        boolean dirty = false;
        if (!plugin.getConfig().isSet(base + ".type")) {
            plugin.getConfig().set(base + ".type", type.toLowerCase(Locale.ROOT));
            dirty = true;
        }
        NamespacedKey def = new NamespacedKey(NAMESPACE, id.toLowerCase(Locale.ROOT));
        if (!w.getKey().equals(def) && !w.getKey().asString().equals(plugin.getConfig().getString(base + ".key"))) {
            plugin.getConfig().set(base + ".key", w.getKey().asString());
            dirty = true;
        }
        if (dirty) plugin.saveConfig();
    }

    public boolean unload(String ref) {
        World w = resolve(ref);
        if (w == null || w.equals(hubWorld())) return false;
        Location safe = hubWorld().getSpawnLocation();
        for (Player p : new ArrayList<>(w.getPlayers())) p.teleport(safe);
        return Bukkit.unloadWorld(w, true);
    }

    public World hubWorld() {
        World w = resolve(plugin.getConfig().getString("hub-world", "minecraft:overworld"));
        return w != null ? w : Bukkit.getWorlds().get(0);
    }

    /** Ids de config + clés des autres mondes chargés (pour la liste et l'autocomplétion). */
    public List<String> knownWorlds() {
        List<String> out = new ArrayList<>();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("worlds");
        if (sec != null) out.addAll(sec.getKeys(false));
        for (World w : Bukkit.getWorlds()) if (idOf(w) == null) out.add(w.getKey().asString());
        return out;
    }

    // ------------------------------------------------------------------ règles par monde

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (p.hasPermission("cghub.bypass")) return;
        String id = idOf(p.getWorld());
        if (id == null) return;
        String gm = plugin.getConfig().getString("worlds." + id + ".gamemode");
        if (gm == null) return;
        try {
            p.setGameMode(GameMode.valueOf(gm.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("gamemode invalide pour " + id + " : " + gm);
        }
    }

    /** PvP par monde, géré par le plugin (indépendant des gamerules). */
    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) attacker = p;
        if (attacker == null || attacker.equals(victim)) return;
        String id = idOf(victim.getWorld());
        if (id != null && !plugin.getConfig().getBoolean("worlds." + id + ".pvp", true)) e.setCancelled(true);
    }
}
