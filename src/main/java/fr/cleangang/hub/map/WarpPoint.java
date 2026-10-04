package fr.cleangang.hub.map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.util.List;

/**
 * Un point posé sur la carte. "world" contient la clé du monde (ex. cleangang:poulet) ;
 * un ancien nom sans ":" reste accepté. Résolu à la demande.
 */
public record WarpPoint(String id, String name, Material icon, int slot, List<String> lore,
                        String world, double x, double y, double z, float yaw, float pitch) {

    public static WarpPoint at(String id, String name, Material icon, int slot, List<String> lore, Location l) {
        return new WarpPoint(id, name, icon, slot, lore, l.getWorld().getKey().asString(),
                l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public World resolveWorld() {
        if (world.indexOf(':') >= 0) {
            NamespacedKey k = NamespacedKey.fromString(world);
            return k == null ? null : Bukkit.getWorld(k);
        }
        return Bukkit.getWorld(world);
    }

    public Location location() {
        World w = resolveWorld();
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    public boolean available() { return resolveWorld() != null; }
}
