package fr.cleangang.hub.world;

import fr.cleangang.hub.CleanGangHub;
import fr.cleangang.hub.util.FileUtil;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Cœur multimonde (Paper 26.x).
 * Un monde = une entrée de worlds.yml (id) + une clé (ex. cleangang:poulet),
 * stocké dans world/dimensions/<namespace>/<clé>/.
 */
public final class WorldService {
    public static final String NAMESPACE = "cleangang";
    public static final List<String> TYPES = List.of("NORMAL", "FLAT", "AMPLIFIED", "LARGE_BIOMES", "VOID");
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9_.\\-]+");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    /** Fichiers à ne pas copier lors d'un clone/backup (verrou + identifiant unique du monde). */
    private static final Predicate<String> SKIP = rel ->
            rel.endsWith("session.lock") || rel.endsWith("uid.dat") || rel.equals("data/paper/metadata.dat");

    private final CleanGangHub plugin;
    private final File file;
    private final Map<String, WorldEntry> entries = new LinkedHashMap<>();
    private final Map<NamespacedKey, Long> loadedAt = new HashMap<>();
    private final Map<NamespacedKey, Long> emptySince = new HashMap<>();

    public WorldService(CleanGangHub plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "worlds.yml");
    }

    public static boolean isValidId(String id) { return id != null && VALID_ID.matcher(id).matches(); }

    // =================================================================== fichier worlds.yml

    public void loadFile() {
        entries.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("worlds");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            NamespacedKey key = NamespacedKey.fromString(s.getString("key", NAMESPACE + ":" + id));
            if (key == null) key = new NamespacedKey(NAMESPACE, id);
            WorldEntry e = new WorldEntry(id, key);
            e.environment = parseEnv(s.getString("environment"), World.Environment.NORMAL);
            e.type = s.getString("type", "NORMAL").toUpperCase(Locale.ROOT);
            e.generator = s.getString("generator", "");
            e.seed = s.isSet("seed") ? s.getLong("seed") : null;
            e.creator = s.getString("creator", "?");
            e.created = s.getLong("created", 0L);
            ConfigurationSection f = s.getConfigurationSection("flags");
            if (f != null) {
                for (String k : f.getKeys(false)) {
                    WorldFlag flag = WorldFlag.byKey(k);
                    if (flag != null) e.flags.put(flag, String.valueOf(f.get(k)));
                }
            }
            for (String u : s.getStringList("whitelist")) {
                try { e.whitelist.add(UUID.fromString(u)); } catch (IllegalArgumentException ignored) { }
            }
            entries.put(id, e);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of(
                "Mondes gérés par CleanGangHub.",
                "Préfère les commandes /world flag, /world link, /world whitelist pour modifier."));
        y.createSection("worlds");
        for (WorldEntry e : entries.values()) {
            String b = "worlds." + e.id + ".";
            y.set(b + "key", e.key.asString());
            y.set(b + "environment", e.environment.name());
            y.set(b + "type", e.type);
            y.set(b + "generator", e.generator);
            if (e.seed != null) y.set(b + "seed", e.seed);
            y.set(b + "creator", e.creator);
            y.set(b + "created", e.created);
            for (Map.Entry<WorldFlag, String> f : e.flags.entrySet()) y.set(b + "flags." + f.getKey().key(), f.getValue());
            y.set(b + "whitelist", e.whitelist.stream().map(UUID::toString).toList());
        }
        try {
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Impossible d'écrire worlds.yml : " + ex.getMessage());
        }
    }

    // =================================================================== démarrage

    public void startup() {
        loadFile();
        migrateV1Config();
        registerLoadedWorlds();
        long now = System.currentTimeMillis();
        for (WorldEntry e : new ArrayList<>(entries.values())) {
            World w = world(e);
            if (w != null) {
                loadedAt.putIfAbsent(w.getKey(), now);
                applyLive(e, w);
                continue;
            }
            if (!bool(e, WorldFlag.AUTO_LOAD)) continue;
            if (!existsOnDisk(e)) {
                plugin.getLogger().warning("Monde '" + e.id + "' introuvable sur le disque (" + e.key.asString() + ") — ignoré.");
                continue;
            }
            try {
                load(e);
            } catch (RuntimeException ex) {
                plugin.getLogger().severe("Chargement de '" + e.id + "' impossible : " + ex.getMessage());
            }
        }
        save();
    }

    /** Ajoute au registre les mondes déjà chargés par le serveur (overworld, nether, end...). */
    private void registerLoadedWorlds() {
        for (World w : Bukkit.getWorlds()) {
            if (entryOf(w) != null) continue;
            String base = w.getKey().getKey().replace('/', '_');
            String id = base;
            for (int i = 2; entries.containsKey(id); i++) id = base + i;
            WorldEntry e = new WorldEntry(id, w.getKey());
            e.environment = w.getEnvironment();
            e.seed = w.getSeed();
            e.creator = "Serveur";
            e.created = System.currentTimeMillis();
            entries.put(id, e);
        }
    }

    /** Reprend l'ancienne section "worlds:" de config.yml (CleanGangHub 1.x). */
    private void migrateV1Config() {
        ConfigurationSection old = plugin.getConfig().getConfigurationSection("worlds");
        if (old == null) return;
        for (String id : old.getKeys(false)) {
            if (entries.containsKey(id)) continue;
            NamespacedKey key = NamespacedKey.fromString(old.getString(id + ".key", NAMESPACE + ":" + id));
            WorldEntry e = new WorldEntry(id, key != null ? key : new NamespacedKey(NAMESPACE, id));
            switch (old.getString(id + ".type", "normal").toLowerCase(Locale.ROOT)) {
                case "void" -> e.type = "VOID";
                case "flat" -> e.type = "FLAT";
                case "nether" -> e.environment = World.Environment.NETHER;
                case "end" -> e.environment = World.Environment.THE_END;
                default -> e.type = "NORMAL";
            }
            String gm = old.getString(id + ".gamemode");
            if (gm != null && WorldFlag.GAME_MODE.normalize(gm) != null) {
                e.flags.put(WorldFlag.GAME_MODE, WorldFlag.GAME_MODE.normalize(gm));
                e.flags.put(WorldFlag.FORCE_GAME_MODE, "false-with-permission");
            }
            if (old.contains(id + ".pvp")) e.flags.put(WorldFlag.PVP, String.valueOf(old.getBoolean(id + ".pvp")));
            e.creator = "migration";
            e.created = System.currentTimeMillis();
            entries.put(id, e);
        }
        plugin.getConfig().set("worlds", null);
        plugin.saveConfig();
        plugin.getLogger().info("Ancienne config des mondes migrée vers worlds.yml.");
    }

    // =================================================================== recherche

    public Collection<WorldEntry> entries() { return entries.values(); }
    public List<String> ids() { return new ArrayList<>(entries.keySet()); }
    public WorldEntry entry(String id) { return id == null ? null : entries.get(id); }

    public WorldEntry entryOf(World w) {
        for (WorldEntry e : entries.values()) if (e.key.equals(w.getKey())) return e;
        return null;
    }

    /** Accepte un id, une clé "ns:clé" ou un ancien nom de monde. */
    public WorldEntry findEntry(String ref) {
        if (ref == null) return null;
        WorldEntry e = entries.get(ref);
        if (e != null) return e;
        NamespacedKey k = ref.indexOf(':') >= 0 ? NamespacedKey.fromString(ref) : null;
        if (k != null) for (WorldEntry x : entries.values()) if (x.key.equals(k)) return x;
        return null;
    }

    public World world(WorldEntry e) { return Bukkit.getWorld(e.key); }

    public World resolve(String ref) {
        if (ref == null || ref.isBlank()) return null;
        WorldEntry e = findEntry(ref);
        if (e != null) return world(e);
        if (ref.indexOf(':') >= 0) {
            NamespacedKey k = NamespacedKey.fromString(ref);
            return k == null ? null : Bukkit.getWorld(k);
        }
        return Bukkit.getWorld(ref);
    }

    public String label(World w) {
        WorldEntry e = entryOf(w);
        return e != null ? e.id : w.getKey().asString();
    }

    public World mainWorld() { return Bukkit.getWorlds().get(0); }
    public boolean isMainWorld(World w) { return w.equals(mainWorld()); }

    public World hubWorld() {
        World w = resolve(plugin.getConfig().getString("hub-world", "minecraft:overworld"));
        return w != null ? w : mainWorld();
    }

    // =================================================================== flags

    public String flag(WorldEntry e, WorldFlag f) {
        String v = e.flags.get(f);
        if (v != null) return v;
        String def = plugin.getConfig().getString("world-defaults." + f.key());
        return def != null ? def : f.fallback();
    }

    public boolean bool(WorldEntry e, WorldFlag f) { return Boolean.parseBoolean(flag(e, f)); }

    /** Valeur d'un flag pour un monde (valeur par défaut si le monde n'est pas géré). */
    public boolean bool(World w, WorldFlag f) {
        WorldEntry e = entryOf(w);
        if (e != null) return bool(e, f);
        String def = plugin.getConfig().getString("world-defaults." + f.key(), f.fallback());
        return Boolean.parseBoolean(def);
    }

    public void setFlag(WorldEntry e, WorldFlag f, String value) {
        e.flags.put(f, value);
        save();
        World w = world(e);
        if (w != null) applyLive(e, w);
    }

    /** Applique ce qui doit l'être directement sur le monde chargé. */
    public void applyLive(WorldEntry e, World w) {
        if (e.flags.containsKey(WorldFlag.DIFFICULTY)) {
            try { w.setDifficulty(Difficulty.valueOf(flag(e, WorldFlag.DIFFICULTY))); } catch (IllegalArgumentException ignored) { }
        }
        if (!bool(e, WorldFlag.WEATHER)) {
            w.setStorm(false);
            w.setThundering(false);
        }
    }

    // =================================================================== disque

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

    public File dimensionFolder(NamespacedKey k) {
        return new File(levelFolder(), "dimensions" + File.separator + k.getNamespace() + File.separator + k.getKey());
    }

    /** Ancien format : dossier avec level.dat à la racine du serveur. */
    public File legacyLevelDat(String id) {
        return new File(Bukkit.getWorldContainer(), id + File.separator + "level.dat");
    }

    public boolean existsOnDisk(WorldEntry e) {
        return dimensionFolder(e.key).isDirectory() || legacyLevelDat(e.id).isFile();
    }

    // =================================================================== chargement

    private void checkGenerator(String generator) {
        if (generator == null || generator.isBlank() || generator.equalsIgnoreCase("void")) return;
        Plugin p = Bukkit.getPluginManager().getPlugin(generator.split(":", 2)[0]);
        if (p == null || !p.isEnabled()) {
            throw new IllegalStateException("Le générateur '" + generator + "' n'est pas installé.");
        }
    }

    public World load(WorldEntry e) {
        World existing = world(e);
        if (existing != null) return existing;
        checkGenerator(e.generator);

        WorldCreator wc;
        if (dimensionFolder(e.key).isDirectory()) {
            wc = WorldCreator.ofKey(e.key);
        } else if (legacyLevelDat(e.id).isFile()) {
            plugin.getLogger().info("Ancien monde '" + e.id + "' : conversion au format 26.x par Paper…");
            wc = new WorldCreator(e.id); // chemin officiel pour les mondes pas encore convertis
        } else {
            wc = WorldCreator.ofKey(e.key);
        }
        wc.environment(e.environment);
        if (e.seed != null) wc.seed(e.seed);

        boolean customGen = e.generator != null && !e.generator.isBlank();
        if (customGen && e.generator.equalsIgnoreCase("void")) wc.generator(new VoidGenerator());
        else if (customGen) wc.generator(e.generator);
        switch (e.type) {
            case "VOID" -> { if (!customGen) wc.generator(new VoidGenerator()); }
            case "FLAT" -> wc.type(WorldType.FLAT);
            case "AMPLIFIED" -> wc.type(WorldType.AMPLIFIED);
            case "LARGE_BIOMES" -> wc.type(WorldType.LARGE_BIOMES);
            default -> wc.type(WorldType.NORMAL);
        }

        World w = wc.createWorld();
        if (w == null) return null;
        if (!w.getKey().equals(e.key)) { // après conversion d'un ancien dossier
            e.key = w.getKey();
            save();
        }
        loadedAt.put(w.getKey(), System.currentTimeMillis());
        applyLive(e, w);
        plugin.getLogger().info("Monde chargé : " + e.id + " -> " + w.getKey().asString());
        return w;
    }

    public World create(String id, World.Environment env, String type, String generator, Long seed, String creator) {
        if (entries.containsKey(id)) throw new IllegalStateException("Un monde nommé " + id + " existe déjà.");
        NamespacedKey key = new NamespacedKey(NAMESPACE, id);
        if (dimensionFolder(key).exists() || legacyLevelDat(id).isFile()) {
            throw new IllegalStateException("Des fichiers existent déjà pour '" + id + "' : utilise /world import " + id);
        }
        checkGenerator(generator);
        WorldEntry e = new WorldEntry(id, key);
        e.environment = env;
        e.type = type;
        e.generator = generator == null ? "" : generator;
        e.seed = seed;
        e.creator = creator;
        e.created = System.currentTimeMillis();
        entries.put(id, e);
        World w;
        try {
            w = load(e);
        } catch (RuntimeException ex) {
            entries.remove(id);
            throw ex;
        }
        if (w == null) {
            entries.remove(id);
            throw new IllegalStateException("Paper n'a pas pu créer le monde.");
        }
        if (type.equals("VOID") && e.generator.isBlank()) {
            Block b = w.getBlockAt(0, 63, 0);
            if (b.getType().isAir()) b.setType(Material.BEDROCK);
            w.setSpawnLocation(0, 64, 0);
        }
        save();
        return w;
    }

    public World importWorld(String id, World.Environment env, String type, String generator, String creator) {
        if (entries.containsKey(id)) throw new IllegalStateException("Le monde " + id + " est déjà importé.");
        WorldEntry e = new WorldEntry(id, new NamespacedKey(NAMESPACE, id));
        if (!existsOnDisk(e)) {
            throw new IllegalStateException("Aucun dossier trouvé : ni " + e.key.asString()
                    + " dans world/dimensions/, ni '" + id + "/level.dat' à la racine.");
        }
        checkGenerator(generator);
        e.environment = env;
        e.type = type;
        e.generator = generator == null ? "" : generator;
        e.creator = creator;
        e.created = System.currentTimeMillis();
        entries.put(id, e);
        World w;
        try {
            w = load(e);
        } catch (RuntimeException ex) {
            entries.remove(id);
            throw ex;
        }
        if (w == null) {
            entries.remove(id);
            throw new IllegalStateException("Paper n'a pas pu charger le monde.");
        }
        save();
        return w;
    }

    public boolean unload(World w, boolean saveChunks) {
        if (isMainWorld(w)) return false;
        World safe = hubWorld().equals(w) ? mainWorld() : hubWorld();
        Location spawn = safe.getSpawnLocation();
        for (Player p : new ArrayList<>(w.getPlayers())) {
            plugin.msg(p, "<gray>Le monde <white>" + label(w) + "<gray> est déchargé, retour au hub.");
            p.teleport(spawn);
        }
        boolean ok = Bukkit.unloadWorld(w, saveChunks);
        if (ok) {
            loadedAt.remove(w.getKey());
            emptySince.remove(w.getKey());
        }
        return ok;
    }

    // =================================================================== opérations fichiers

    /** Supprime définitivement un monde. callback(erreur ou null). */
    public void delete(WorldEntry e, java.util.function.Consumer<String> done) {
        if (e.isVanilla()) { done.accept("Les mondes vanilla ne peuvent pas être supprimés."); return; }
        World w = world(e);
        if (w != null && (isMainWorld(w) || w.equals(hubWorld()))) { done.accept("Impossible de supprimer le monde hub."); return; }
        if (w != null && !unload(w, false)) { done.accept("Impossible de décharger le monde."); return; }
        entries.remove(e.id);
        save();
        File dim = dimensionFolder(e.key);
        File legacy = legacyLevelDat(e.id);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String err = null;
            try {
                FileUtil.deleteDir(dim.toPath());
                if (legacy.isFile()) FileUtil.deleteDir(legacy.getParentFile().toPath());
            } catch (IOException ex) {
                err = ex.getMessage();
            }
            String finalErr = err;
            Bukkit.getScheduler().runTask(plugin, () -> done.accept(finalErr));
        });
    }

    /** Copie un monde sous un nouvel id. callback(monde, erreur). */
    public void cloneWorld(WorldEntry src, String newId, String creator, BiConsumer<World, String> done) {
        if (entries.containsKey(newId)) { done.accept(null, "Un monde nommé " + newId + " existe déjà."); return; }
        File from = dimensionFolder(src.key);
        if (!from.isDirectory()) { done.accept(null, "Le monde source n'est pas au format 26.x (charge-le une fois)."); return; }
        NamespacedKey nk = new NamespacedKey(NAMESPACE, newId);
        File to = dimensionFolder(nk);
        if (to.exists()) { done.accept(null, "Le dossier " + to.getPath() + " existe déjà."); return; }
        World w = world(src);
        if (w != null) w.save();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String err = null;
            try {
                FileUtil.copyDir(from.toPath(), to.toPath(), SKIP);
            } catch (IOException ex) {
                err = ex.getMessage();
            }
            String finalErr = err;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (finalErr != null) { done.accept(null, finalErr); return; }
                WorldEntry c = new WorldEntry(newId, nk);
                c.environment = src.environment;
                c.type = src.type;
                c.generator = src.generator;
                c.seed = src.seed;
                c.creator = creator;
                c.created = System.currentTimeMillis();
                c.flags.putAll(src.flags);
                c.whitelist.addAll(src.whitelist);
                entries.put(newId, c);
                save();
                try {
                    done.accept(load(c), null);
                } catch (RuntimeException ex) {
                    done.accept(null, ex.getMessage());
                }
            });
        });
    }

    /** Sauvegarde zip dans plugins/CleanGangHub/backups/. callback(fichier, erreur). */
    public void backup(WorldEntry e, BiConsumer<File, String> done) {
        File src = dimensionFolder(e.key);
        if (!src.isDirectory()) { done.accept(null, "Dossier introuvable : " + src.getPath()); return; }
        World w = world(e);
        if (w != null) w.save();
        File zip = new File(plugin.getDataFolder(), "backups" + File.separator + e.id + "_" + LocalDateTime.now().format(STAMP) + ".zip");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String err = null;
            try {
                FileUtil.zipDir(src.toPath(), zip, rel -> rel.endsWith("session.lock"));
            } catch (IOException ex) {
                err = ex.getMessage();
            }
            String finalErr = err;
            Bukkit.getScheduler().runTask(plugin, () -> done.accept(finalErr == null ? zip : null, finalErr));
        });
    }

    // =================================================================== auto-unload

    public void tickAutoUnload() {
        long now = System.currentTimeMillis();
        long delay = plugin.getConfig().getLong("auto-unload.delay", 1800) * 1000L;
        long loadDelay = plugin.getConfig().getLong("auto-unload.load-delay", 300) * 1000L;
        for (WorldEntry e : new ArrayList<>(entries.values())) {
            if (!bool(e, WorldFlag.AUTO_UNLOAD)) continue;
            World w = world(e);
            if (w == null || isMainWorld(w) || w.equals(hubWorld())) continue;
            if (!w.getPlayers().isEmpty()) {
                emptySince.remove(w.getKey());
                continue;
            }
            long since = emptySince.computeIfAbsent(w.getKey(), k -> now);
            if (now - since >= delay && now - loadedAt.getOrDefault(w.getKey(), 0L) >= loadDelay) {
                if (unload(w, true)) plugin.getLogger().info("Auto-unload : " + e.id + " (inactif).");
            }
        }
    }

    public static World.Environment parseEnv(String s, World.Environment def) {
        if (s == null) return def;
        return switch (s.toUpperCase(Locale.ROOT)) {
            case "NORMAL", "OVERWORLD" -> World.Environment.NORMAL;
            case "NETHER", "THE_NETHER" -> World.Environment.NETHER;
            case "END", "THE_END" -> World.Environment.THE_END;
            default -> def;
        };
    }
}
