package fr.cleangang.hub.map;

import fr.cleangang.hub.CleanGangHub;
import fr.cleangang.hub.util.Items;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PointStore {
    private final CleanGangHub plugin;
    private final File file;
    private final Map<String, WarpPoint> points = new LinkedHashMap<>();

    public PointStore(CleanGangHub plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "points.yml");
    }

    public void load() {
        points.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("points");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            points.put(id, new WarpPoint(
                    id,
                    s.getString("name", "<yellow>" + id),
                    Items.material(s.getString("icon"), Material.ENDER_PEARL),
                    s.getInt("slot", -1),
                    s.getStringList("lore"),
                    s.getString("world", "world"),
                    s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    (float) s.getDouble("yaw"), (float) s.getDouble("pitch")));
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.createSection("points");
        for (WarpPoint p : points.values()) {
            String b = "points." + p.id() + ".";
            y.set(b + "name", p.name());
            y.set(b + "icon", p.icon().name());
            y.set(b + "slot", p.slot());
            y.set(b + "lore", p.lore());
            y.set(b + "world", p.world());
            y.set(b + "x", p.x());
            y.set(b + "y", p.y());
            y.set(b + "z", p.z());
            y.set(b + "yaw", (double) p.yaw());
            y.set(b + "pitch", (double) p.pitch());
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible d'écrire points.yml : " + e.getMessage());
        }
    }

    public WarpPoint get(String id) { return id == null ? null : points.get(id); }
    public Collection<WarpPoint> all() { return points.values(); }
    public void put(WarpPoint p) { points.put(p.id(), p); save(); }
    public boolean remove(String id) { boolean r = points.remove(id) != null; if (r) save(); return r; }
}
