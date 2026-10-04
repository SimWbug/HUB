package fr.cleangang.hub.command;

import fr.cleangang.hub.CleanGangHub;
import fr.cleangang.hub.util.Text;
import fr.cleangang.hub.world.WorldEntry;
import fr.cleangang.hub.world.WorldFlag;
import fr.cleangang.hub.world.WorldService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * /world (alias /mw, /multiworld) — gestion complète des mondes.
 * back | backup | clone | create | delete | flag | gamerule | help | import | info | link
 * list | load | reload | setspawn | teleport | unload | version | whitelist
 */
public final class WorldCommand implements TabExecutor {
    private static final List<String> SUBS = List.of("back", "backup", "clone", "create", "delete", "flag",
            "gamerule", "help", "import", "info", "link", "list", "load", "reload", "setspawn", "teleport",
            "unload", "version", "whitelist");

    private final CleanGangHub plugin;
    private final WorldService ws;

    public WorldCommand(CleanGangHub plugin) {
        this.plugin = plugin;
        this.ws = plugin.worlds();
    }

    private void msg(CommandSender s, String m) { plugin.msg(s, m); }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("tp")) sub = "teleport";
        String perm = sub.equals("back") ? "cghub.world.back" : "cghub.world";
        if (!s.hasPermission(perm)) {
            msg(s, "<red>Tu n'as pas la permission.");
            return true;
        }
        switch (sub) {
            case "back" -> back(s);
            case "backup" -> backup(s, a);
            case "clone" -> cloneCmd(s, a);
            case "create" -> create(s, a);
            case "delete" -> delete(s, a);
            case "flag" -> flag(s, a);
            case "gamerule" -> gamerule(s, a);
            case "import" -> importCmd(s, a);
            case "info" -> info(s, a);
            case "link" -> link(s, a);
            case "list" -> list(s);
            case "load" -> load(s, a);
            case "reload" -> { plugin.reloadAll(); msg(s, "<green>Configuration rechargée."); }
            case "setspawn" -> setspawn(s);
            case "teleport" -> teleport(s, a);
            case "unload" -> unload(s, a);
            case "version" -> msg(s, "<gray>CleanGangHub <white>" + plugin.getPluginMeta().getVersion()
                    + "<gray> — Paper <white>" + Bukkit.getMinecraftVersion());
            case "whitelist" -> whitelist(s, a);
            default -> help(s);
        }
        return true;
    }

    // ================================================================== utilitaires

    private WorldEntry needEntry(CommandSender s, String ref) {
        WorldEntry e = ws.findEntry(ref);
        if (e == null) msg(s, "<red>Le monde <white>" + ref + "<red> n'existe pas. <gray>(/world list)");
        return e;
    }

    private World needLoaded(CommandSender s, WorldEntry e) {
        World w = ws.world(e);
        if (w == null) msg(s, "<red>Le monde <white>" + e.id + "<red> n'est pas chargé. <gray>(/world load " + e.id + ")");
        return w;
    }

    private record Options(World.Environment env, String type, String generator, Long seed, String error) {}

    /** Lit [environnement] [-g générateur] [-s seed] [-t type] à partir de l'index donné. */
    private Options parseOptions(String[] a, int start) {
        World.Environment env = World.Environment.NORMAL;
        String type = "NORMAL", gen = "";
        Long seed = null;
        for (int i = start; i < a.length; i++) {
            String arg = a[i];
            if (arg.startsWith("-")) {
                if (i + 1 >= a.length) return new Options(null, null, null, null, "Valeur manquante après " + arg);
                String v = a[++i];
                switch (arg.toLowerCase(Locale.ROOT)) {
                    case "-g" -> gen = v;
                    case "-s" -> {
                        try { seed = Long.parseLong(v); } catch (NumberFormatException ex) { seed = (long) v.hashCode(); }
                    }
                    case "-t" -> {
                        type = v.toUpperCase(Locale.ROOT);
                        if (!WorldService.TYPES.contains(type)) {
                            return new Options(null, null, null, null, "Type inconnu : " + v + " (" + String.join(", ", WorldService.TYPES) + ")");
                        }
                    }
                    default -> { return new Options(null, null, null, null, "Option inconnue : " + arg); }
                }
            } else {
                World.Environment e = WorldService.parseEnv(arg, null);
                if (e == null) return new Options(null, null, null, null, "Environnement inconnu : " + arg + " (NORMAL, NETHER, THE_END)");
                env = e;
            }
        }
        if (gen.equalsIgnoreCase("void")) { gen = ""; type = "VOID"; }
        return new Options(env, type, gen, seed, null);
    }

    // ================================================================== commandes

    private void help(CommandSender s) {
        msg(s, "<gold><bold>Commandes /world");
        for (String l : List.of(
                "/world list <dark_gray>— tous les mondes",
                "/world info <monde>",
                "/world create <nom> [NORMAL|NETHER|THE_END] [-t type] [-g générateur] [-s seed]",
                "/world import <nom> [environnement] [-t type] [-g générateur]",
                "/world load <monde> <dark_gray>|</dark_gray> /world unload <monde>",
                "/world tp [joueur] <monde> <dark_gray>|</dark_gray> /world back <dark_gray>|</dark_gray> /world setspawn",
                "/world clone <monde> <nouveau> <dark_gray>|</dark_gray> /world backup <monde>",
                "/world delete <monde> confirm",
                "/world flag <monde> <flag> <valeur>",
                "/world gamerule <monde> <règle> [valeur]",
                "/world link <monde> <nether|end|normal> <cible>",
                "/world whitelist <monde> <on|off|add|remove|list> [joueur]",
                "/world reload <dark_gray>|</dark_gray> /world version",
                "<dark_gray>Types : NORMAL, FLAT, AMPLIFIED, LARGE_BIOMES, VOID")) {
            s.sendMessage(Text.mm(" <gray>" + l));
        }
    }

    private void list(CommandSender s) {
        msg(s, "<gold>Mondes :");
        for (WorldEntry e : ws.entries()) {
            World w = ws.world(e);
            s.sendMessage(Text.mm(w != null
                    ? " <green>● <white>" + e.id + " <dark_gray>" + e.key.asString() + " <gray>" + w.getPlayers().size() + " joueur(s)"
                    : " <red>○ <gray>" + e.id + " <dark_gray>" + e.key.asString() + " (non chargé)"));
        }
    }

    private void info(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/world info <monde>"); return; }
        WorldEntry e = needEntry(s, a[1]);
        if (e == null) return;
        World w = ws.world(e);
        SimpleDateFormat fmt = new SimpleDateFormat(plugin.getConfig().getString("time-format", "dd/MM/yyyy HH:mm"));
        List<String> lines = new ArrayList<>();
        lines.add("<dark_gray>───── <aqua><bold>" + e.id + "</bold> <dark_gray>─────");
        lines.add("<gray>Clé : <white>" + e.key.asString() + (w != null ? " <green>(chargé, " + w.getPlayers().size() + " joueur(s))" : " <red>(non chargé)"));
        lines.add("<gray>Créé par <white>" + e.creator + "<gray> le <white>" + (e.created > 0 ? fmt.format(new Date(e.created)) : "?"));
        lines.add("<gray>Environnement : <white>" + e.environment + " <gray>Type : <white>" + e.type
                + (e.generator.isBlank() ? "" : " <gray>Générateur : <white>" + e.generator)
                + (e.seed != null ? " <gray>Seed : <white>" + e.seed : ""));
        for (WorldFlag f : WorldFlag.values()) {
            String v = ws.flag(e, f);
            String col = v.equals("true") ? "<green>" : v.equals("false") ? "<red>" : "<white>";
            lines.add(" <dark_gray>» <gray>" + f.key() + " : " + col + v + (e.flags.containsKey(f) ? "" : " <dark_gray>(défaut)"));
        }
        lines.add("<gray>Whitelist : <white>" + e.whitelist.size() + " joueur(s)");
        for (String l : lines) s.sendMessage(Text.mm(l));
    }

    private void create(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/world create <nom> [NORMAL|NETHER|THE_END] [-t type] [-g générateur] [-s seed]"); return; }
        String id = a[1].toLowerCase(Locale.ROOT);
        if (!WorldService.isValidId(id)) { msg(s, "<red>Nom invalide : minuscules, chiffres, _ - . uniquement."); return; }
        Options o = parseOptions(a, 2);
        if (o.error() != null) { msg(s, "<red>" + o.error()); return; }
        msg(s, "<gray>Création de <white>" + id + "<gray>…");
        try {
            World w = ws.create(id, o.env(), o.type(), o.generator(), o.seed(), s.getName());
            msg(s, "<green>Monde <white>" + id + "<green> créé <dark_gray>(" + w.getKey().asString() + ")");
        } catch (RuntimeException ex) {
            msg(s, "<red>" + ex.getMessage());
        }
    }

    private void importCmd(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/world import <nom> [environnement] [-t type] [-g générateur]"); return; }
        String id = a[1].toLowerCase(Locale.ROOT);
        if (!WorldService.isValidId(id)) { msg(s, "<red>Nom invalide : le dossier doit être en minuscules (a-z 0-9 _ - .)."); return; }
        Options o = parseOptions(a, 2);
        if (o.error() != null) { msg(s, "<red>" + o.error()); return; }
        msg(s, "<gray>Import de <white>" + id + "<gray>… <dark_gray>(un ancien format est converti par Paper)");
        try {
            World w = ws.importWorld(id, o.env(), o.type(), o.generator(), s.getName());
            msg(s, "<green>Monde <white>" + id + "<green> importé <dark_gray>(" + w.getKey().asString() + ")");
        } catch (RuntimeException ex) {
            msg(s, "<red>" + ex.getMessage());
        }
    }

    private void load(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/world load <monde>"); return; }
        WorldEntry e = needEntry(s, a[1]);
        if (e == null) return;
        if (ws.world(e) != null) { msg(s, "<yellow>Le monde est déjà chargé."); return; }
        if (!ws.existsOnDisk(e)) { msg(s, "<red>Fichiers du monde introuvables (" + e.key.asString() + ")."); return; }
        msg(s, "<gray>Chargement de <white>" + e.id + "<gray>…");
        try {
            World w = ws.load(e);
            msg(s, w != null ? "<green>Monde <white>" + e.id + "<green> chargé." : "<red>Échec du chargement (voir la console).");
        } catch (RuntimeException ex) {
            msg(s, "<red>" + ex.getMessage());
        }
    }

    private void unload(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/world unload <monde>"); return; }
        WorldEntry e = needEntry(s, a[1]);
        if (e == null) return;
        World w = needLoaded(s, e);
        if (w == null) return;
        msg(s, ws.unload(w, true) ? "<green>Monde <white>" + e.id + "<green> déchargé." : "<red>Ce monde ne peut pas être déchargé.");
    }

    private void delete(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/world delete <monde> confirm"); return; }
        WorldEntry e = needEntry(s, a[1]);
        if (e == null) return;
        if (a.length < 3 || !a[2].equalsIgnoreCase("confirm")) {
            msg(s, "<yellow>⚠ Ceci supprime définitivement <white>" + e.id + "<yellow>. Tape <white>/world delete " + e.id + " confirm");
            return;
        }
        msg(s, "<gray>Suppression de <white>" + e.id + "<gray>…");
        ws.delete(e, err -> msg(s, err == null ? "<green>Monde <white>" + e.id + "<green> supprimé." : "<red>" + err));
    }

    private void cloneCmd(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "<red>/world clone <monde> <nouveau nom>"); return; }
        WorldEntry e = needEntry(s, a[1]);
        if (e == null) return;
        String id = a[2].toLowerCase(Locale.ROOT);
        if (!WorldService.isValidId(id)) { msg(s, "<red>Nom invalide."); return; }
        msg(s, "<gray>Clonage de <white>" + e.id + "<gray> vers <white>" + id + "<gray>…");
        ws.cloneWorld(e, id, s.getName(), (w, err) -> msg(s, err == null
                ? "<green>Monde cloné : <white>" + id + " <dark_gray>(" + w.getKey().asString() + ")"
                : "<red>" + err));
    }

    private void backup(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/world backup <monde>"); return; }
        WorldEntry e = needEntry(s, a[1]);
        if (e == null) return;
        msg(s, "<gray>Backup de <white>" + e.id + "<gray> en cours…");
        ws.backup(e, (f, err) -> msg(s, err == null
                ? "<green>Backup créé : <white>backups/" + f.getName()
                : "<red>" + err));
    }

    private void teleport(CommandSender s, String[] a) {
        if (a.length < 2) { msg(s, "<red>/world tp [joueur] <monde>"); return; }
        Player target;
        String ref;
        if (a.length >= 3) {
            target = Bukkit.getPlayerExact(a[1]);
            ref = a[2];
            if (target == null) { msg(s, "<red>Joueur introuvable : " + a[1]); return; }
        } else {
            if (!(s instanceof Player p)) { msg(s, "<red>/world tp <joueur> <monde>"); return; }
            target = p;
            ref = a[1];
        }
        WorldEntry e = needEntry(s, ref);
        if (e == null) return;
        World w = needLoaded(s, e);
        if (w == null) return;
        if (target.getWorld().equals(w)) { msg(s, "<yellow>" + target.getName() + " est déjà dans " + e.id + "."); return; }
        plugin.teleporter().teleport(target, w.getSpawnLocation().add(0.5, 0, 0.5), "<white>" + e.id);
        if (target != s) msg(s, "<green>" + target.getName() + " est téléporté vers <white>" + e.id + "<green>.");
    }

    private void back(CommandSender s) {
        if (!(s instanceof Player p)) return;
        Location l = plugin.back().get(p.getUniqueId());
        if (l == null) { msg(s, "<yellow>Aucune position précédente enregistrée."); return; }
        if (l.getWorld() == null) { msg(s, "<red>Le monde <white>" + plugin.back().worldOf(p.getUniqueId()) + "<red> n'est pas chargé."); return; }
        if (l.getWorld().equals(p.getWorld())) { msg(s, "<yellow>Tu es déjà dans ce monde."); return; }
        plugin.teleporter().teleport(p, l, "<white>" + ws.label(l.getWorld()));
    }

    private void setspawn(CommandSender s) {
        if (!(s instanceof Player p)) return;
        p.getWorld().setSpawnLocation(p.getLocation());
        msg(s, "<green>Spawn de <white>" + ws.label(p.getWorld()) + "<green> défini ici.");
    }

    private void flag(CommandSender s, String[] a) {
        if (a.length < 4) { msg(s, "<red>/world flag <monde> <flag> <valeur>"); return; }
        WorldEntry e = needEntry(s, a[1]);
        if (e == null) return;
        WorldFlag f = WorldFlag.byKey(a[2]);
        if (f == null) { msg(s, "<red>Flag inconnu : " + a[2] + " <gray>(" + String.join(", ", WorldFlag.keys()) + ")"); return; }
        String v = f.normalize(a[3]);
        if (v == null) { msg(s, "<red>Valeur invalide pour " + f.key() + " <gray>(" + String.join(", ", f.suggestions()) + ")"); return; }
        if (f.kind() == WorldFlag.Kind.WORLD && ws.findEntry(v) == null) { msg(s, "<red>Monde inconnu : " + v); return; }
        ws.setFlag(e, f, v);
        msg(s, "<green>" + e.id + " <gray>» <white>" + f.key() + " <gray>= <white>" + v);
        if (f == WorldFlag.GAME_MODE || f == WorldFlag.FORCE_GAME_MODE) {
            World w = ws.world(e);
            if (w != null) w.getPlayers().forEach(plugin.playerWorldListener()::applyGameMode);
        }
        if (f == WorldFlag.WHITELIST && v.equals("true")) kickNonWhitelisted(e);
    }

    private void gamerule(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "<red>/world gamerule <monde> <règle> [valeur]"); return; }
        WorldEntry e = needEntry(s, a[1]);
        if (e == null) return;
        if (needLoaded(s, e) == null) return;
        // On passe par la commande vanilla : toujours à jour avec les noms de règles de la version.
        String command = "execute in " + e.key.asString() + " run gamerule " + a[2] + (a.length > 3 ? " " + a[3] : "");
        Bukkit.dispatchCommand(s, command);
    }

    private void link(CommandSender s, String[] a) {
        if (a.length < 4) { msg(s, "<red>/world link <monde> <nether|end|normal> <cible>"); return; }
        WorldEntry e = needEntry(s, a[1]);
        if (e == null) return;
        WorldFlag f = switch (a[2].toLowerCase(Locale.ROOT)) {
            case "nether" -> WorldFlag.NETHER_WORLD;
            case "end" -> WorldFlag.END_WORLD;
            case "normal", "overworld" -> WorldFlag.NORMAL_WORLD;
            default -> null;
        };
        if (f == null) { msg(s, "<red>Type de lien : nether, end ou normal."); return; }
        WorldEntry target = needEntry(s, a[3]);
        if (target == null) return;
        ws.setFlag(e, f, target.id);
        msg(s, "<green>Portail <white>" + a[2].toLowerCase(Locale.ROOT) + "<green> de <white>" + e.id
                + "<green> relié à <white>" + target.id + "<green>.");
    }

    private void whitelist(CommandSender s, String[] a) {
        if (a.length < 3) { msg(s, "<red>/world whitelist <monde> <on|off|add|remove|list> [joueur]"); return; }
        WorldEntry e = needEntry(s, a[1]);
        if (e == null) return;
        switch (a[2].toLowerCase(Locale.ROOT)) {
            case "on", "enable" -> {
                ws.setFlag(e, WorldFlag.WHITELIST, "true");
                kickNonWhitelisted(e);
                msg(s, "<green>Whitelist de <white>" + e.id + "<green> activée.");
            }
            case "off", "disable" -> {
                ws.setFlag(e, WorldFlag.WHITELIST, "false");
                msg(s, "<green>Whitelist de <white>" + e.id + "<green> désactivée.");
            }
            case "list" -> {
                if (e.whitelist.isEmpty()) { msg(s, "<gray>Whitelist de " + e.id + " vide."); return; }
                List<String> names = new ArrayList<>();
                for (UUID u : e.whitelist) {
                    String n = Bukkit.getOfflinePlayer(u).getName();
                    names.add(n != null ? n : u.toString());
                }
                msg(s, "<gray>Whitelist de <white>" + e.id + "<gray> : <white>" + String.join(", ", names));
            }
            case "add", "remove" -> {
                if (a.length < 4) { msg(s, "<red>Précise un joueur."); return; }
                OfflinePlayer op = Bukkit.getPlayerExact(a[3]);
                if (op == null) op = Bukkit.getOfflinePlayerIfCached(a[3]);
                if (op == null) { msg(s, "<red>Joueur inconnu (il doit s'être connecté au moins une fois)."); return; }
                boolean add = a[2].equalsIgnoreCase("add");
                boolean changed = add ? e.whitelist.add(op.getUniqueId()) : e.whitelist.remove(op.getUniqueId());
                ws.save();
                if (!add) kickNonWhitelisted(e);
                msg(s, changed
                        ? "<green>" + a[3] + (add ? " ajouté à" : " retiré de") + " la whitelist de <white>" + e.id + "<green>."
                        : "<yellow>" + a[3] + (add ? " est déjà" : " n'est pas") + " sur la whitelist.");
            }
            default -> msg(s, "<red>/world whitelist <monde> <on|off|add|remove|list> [joueur]");
        }
    }

    private void kickNonWhitelisted(WorldEntry e) {
        World w = ws.world(e);
        if (w == null) return;
        Location hub = ws.hubWorld().getSpawnLocation();
        for (Player p : new ArrayList<>(w.getPlayers())) {
            if (!plugin.playerWorldListener().canEnter(p, w)) {
                p.teleport(hub);
                msg(p, "<red>Tu n'es pas sur la whitelist de <white>" + e.id + "<red>, retour au hub.");
            }
        }
    }

    // ================================================================== tab

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        List<String> o = new ArrayList<>();
        String sub = a.length > 0 ? a[0].toLowerCase(Locale.ROOT) : "";
        if (a.length == 1) o.addAll(SUBS);
        else if (a.length == 2) {
            switch (sub) {
                case "create", "back", "list", "reload", "version", "help", "setspawn" -> { }
                case "import" -> o.addAll(importCandidates());
                case "tp", "teleport" -> { o.addAll(ws.ids()); o.addAll(players()); }
                default -> o.addAll(ws.ids());
            }
        } else if (a.length == 3) {
            switch (sub) {
                case "flag" -> o.addAll(WorldFlag.keys());
                case "link" -> o.addAll(List.of("nether", "end", "normal"));
                case "whitelist" -> o.addAll(List.of("on", "off", "add", "remove", "list"));
                case "delete" -> o.add("confirm");
                case "tp", "teleport" -> o.addAll(ws.ids());
                case "create", "import" -> o.addAll(List.of("NORMAL", "NETHER", "THE_END", "-t", "-g", "-s"));
                default -> { }
            }
        } else if (a.length == 4) {
            switch (sub) {
                case "flag" -> {
                    WorldFlag f = WorldFlag.byKey(a[2]);
                    if (f != null) o.addAll(f.kind() == WorldFlag.Kind.WORLD ? ws.ids() : f.suggestions());
                }
                case "link" -> o.addAll(ws.ids());
                case "whitelist" -> o.addAll(players());
                case "gamerule" -> o.addAll(List.of("true", "false"));
                default -> { }
            }
        }
        if ((sub.equals("create") || sub.equals("import")) && a.length >= 4) {
            String prev = a[a.length - 2].toLowerCase(Locale.ROOT);
            if (prev.equals("-t")) o = new ArrayList<>(WorldService.TYPES);
            else if (prev.equals("-g")) o = new ArrayList<>(List.of("void"));
            else o.addAll(List.of("-t", "-g", "-s"));
        }
        String last = a.length == 0 ? "" : a[a.length - 1].toLowerCase(Locale.ROOT);
        return o.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(last)).distinct().toList();
    }

    private List<String> players() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }

    /** Dossiers à la racine contenant un level.dat et pas encore importés. */
    private List<String> importCandidates() {
        List<String> out = new ArrayList<>();
        java.io.File[] dirs = Bukkit.getWorldContainer().listFiles(java.io.File::isDirectory);
        if (dirs == null) return out;
        for (java.io.File d : dirs) {
            if (new java.io.File(d, "level.dat").isFile() && !new java.io.File(d, "dimensions").isDirectory()
                    && ws.entry(d.getName()) == null
                    && WorldService.isValidId(d.getName())) out.add(d.getName());
        }
        return out;
    }
}
