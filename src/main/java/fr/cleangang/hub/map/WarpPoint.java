package fr.cleangang.hub.map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.List;

/** Un point posé sur la carte. Le monde est résolu à la demande (il peut être chargé plus tard). */
public record WarpPoint(String id, String name, Material icon, int slot, List<String> lore,
                        String world, double x, double y, double z, float yaw, float pitch) {

    public static WarpPoint at(String id, String name, Material icon, int slot, List<String> lore, Location l) {
        return new WarpPoint(id, name, icon, slot, lore, l.getWorld().getName(),
                l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    public Location location() {
        World w = Bukkit.getWorld(world);
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    public boolean available() { return Bukkit.getWorld(world) != null; }
}
