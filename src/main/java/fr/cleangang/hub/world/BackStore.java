package fr.cleangang.hub.world;

import fr.cleangang.hub.CleanGangHub;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Dernière position de chaque joueur avant un changement de monde (pour /world back). */
public final class BackStore {
    private record Saved(String world, double x, double y, double z, float yaw, float pitch) {}

    private final CleanGangHub plugin;
    private final File file;
    private final Map<UUID, Saved> data = new HashMap<>();

    public BackStore(CleanGangHub plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "users.yml");
    }

    public void load() {
        data.clear();
        ConfigurationSection root = YamlConfiguration.loadConfiguration(file).getConfigurationSection("back");
        if (root == null) return;
        for (String k : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(k);
            if (s == null) continue;
            try {
                data.put(UUID.fromString(k), new Saved(s.getString("world"), s.getDouble("x"), s.getDouble("y"),
                        s.getDouble("z"), (float) s.getDouble("yaw"), (float) s.getDouble("pitch")));
            } catch (IllegalArgumentException ignored) { }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        data.forEach((id, s) -> {
            String b = "back." + id + ".";
            y.set(b + "world", s.world());
            y.set(b + "x", s.x());
            y.set(b + "y", s.y());
            y.set(b + "z", s.z());
            y.set(b + "yaw", (double) s.yaw());
            y.set(b + "pitch", (double) s.pitch());
        });
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("users.yml : " + e.getMessage());
        }
    }

    public void put(UUID id, Location l) {
        data.put(id, new Saved(l.getWorld().getKey().asString(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch()));
    }

    /** null si rien d'enregistré ; Location avec monde null si le monde n'est pas chargé. */
    public Location get(UUID id) {
        Saved s = data.get(id);
        if (s == null) return null;
        NamespacedKey k = NamespacedKey.fromString(s.world());
        World w = k == null ? null : Bukkit.getWorld(k);
        return new Location(w, s.x(), s.y(), s.z(), s.yaw(), s.pitch());
    }

    public String worldOf(UUID id) {
        Saved s = data.get(id);
        return s == null ? null : s.world();
    }
}
