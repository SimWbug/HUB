package fr.cleangang.hub.world;

import org.bukkit.NamespacedKey;
import org.bukkit.World;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Un monde enregistré dans worlds.yml. */
public final class WorldEntry {
    public final String id;
    public NamespacedKey key;
    public World.Environment environment = World.Environment.NORMAL;
    public String type = "NORMAL";       // NORMAL, FLAT, AMPLIFIED, LARGE_BIOMES, VOID
    public String generator = "";        // "" ou "Plugin[:id]"
    public Long seed;
    public String creator = "?";
    public long created;
    public final Map<WorldFlag, String> flags = new EnumMap<>(WorldFlag.class);
    public final Set<UUID> whitelist = new LinkedHashSet<>();

    public WorldEntry(String id, NamespacedKey key) {
        this.id = id;
        this.key = key;
    }

    public boolean isVanilla() { return "minecraft".equals(key.getNamespace()); }
}
