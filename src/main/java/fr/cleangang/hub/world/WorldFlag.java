package fr.cleangang.hub.world;

import org.bukkit.Difficulty;
import org.bukkit.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Réglages par monde (équivalent des flags de MultiWorld). */
public enum WorldFlag {
    AUTO_LOAD("auto-load-enabled", Kind.BOOLEAN, "true"),
    AUTO_UNLOAD("auto-unload-enabled", Kind.BOOLEAN, "false"),
    DIFFICULTY("difficulty", Kind.DIFFICULTY, "NORMAL"),
    GAME_MODE("game-mode", Kind.GAMEMODE, "SURVIVAL"),
    FORCE_GAME_MODE("force-game-mode", Kind.FORCE, "false"),
    HUNGER("hunger-enabled", Kind.BOOLEAN, "true"),
    NETHER_PORTAL("nether-portal-accessible", Kind.BOOLEAN, "true"),
    END_PORTAL("end-portal-accessible", Kind.BOOLEAN, "true"),
    NETHER_WORLD("nether-world", Kind.WORLD, "the_nether"),
    END_WORLD("end-world", Kind.WORLD, "the_end"),
    NORMAL_WORLD("normal-world", Kind.WORLD, "overworld"),
    PVP("pvp-enabled", Kind.BOOLEAN, "true"),
    ADVANCEMENTS("receive-achievements", Kind.BOOLEAN, "true"),
    REDSTONE("redstone-enabled", Kind.BOOLEAN, "true"),
    SPAWN_ANIMALS("spawn-animals", Kind.BOOLEAN, "true"),
    SPAWN_MONSTERS("spawn-monsters", Kind.BOOLEAN, "true"),
    SPAWN_ENTITIES("spawn-entities", Kind.BOOLEAN, "true"),
    WEATHER("weather-enabled", Kind.BOOLEAN, "true"),
    WHITELIST("whitelist-enabled", Kind.BOOLEAN, "false");

    public enum Kind { BOOLEAN, DIFFICULTY, GAMEMODE, FORCE, WORLD }

    private final String key;
    private final Kind kind;
    private final String fallback;

    WorldFlag(String key, Kind kind, String fallback) {
        this.key = key;
        this.kind = kind;
        this.fallback = fallback;
    }

    public String key() { return key; }
    public Kind kind() { return kind; }
    public String fallback() { return fallback; }

    public static WorldFlag byKey(String k) {
        if (k == null) return null;
        for (WorldFlag f : values()) if (f.key.equalsIgnoreCase(k)) return f;
        return null;
    }

    public static List<String> keys() {
        List<String> out = new ArrayList<>();
        for (WorldFlag f : values()) out.add(f.key);
        return out;
    }

    /** Valeur canonique, ou null si invalide. Les flags WORLD sont validés par l'appelant. */
    public String normalize(String v) {
        if (v == null) return null;
        String u = v.trim().toUpperCase(Locale.ROOT);
        try {
            return switch (kind) {
                case BOOLEAN -> switch (u) {
                    case "TRUE", "ON", "OUI", "YES", "1" -> "true";
                    case "FALSE", "OFF", "NON", "NO", "0" -> "false";
                    default -> null;
                };
                case DIFFICULTY -> Difficulty.valueOf(u).name();
                case GAMEMODE -> GameMode.valueOf(u).name();
                case FORCE -> switch (u) {
                    case "TRUE", "ON" -> "true";
                    case "FALSE", "OFF" -> "false";
                    case "FALSE-WITH-PERMISSION" -> "false-with-permission";
                    default -> null;
                };
                case WORLD -> v.trim();
            };
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public List<String> suggestions() {
        return switch (kind) {
            case BOOLEAN -> List.of("true", "false");
            case DIFFICULTY -> List.of("PEACEFUL", "EASY", "NORMAL", "HARD");
            case GAMEMODE -> List.of("SURVIVAL", "CREATIVE", "ADVENTURE", "SPECTATOR");
            case FORCE -> List.of("true", "false", "false-with-permission");
            case WORLD -> List.of();
        };
    }
}
