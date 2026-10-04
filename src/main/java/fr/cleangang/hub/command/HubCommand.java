package fr.cleangang.hub.command;

import fr.cleangang.hub.CleanGangHub;
import fr.cleangang.hub.map.WarpPoint;
import fr.cleangang.hub.util.Items;
import fr.cleangang.hub.util.Text;
import fr.cleangang.hub.world.WorldManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * /cghub world list|create|load|unload|tp|setspawn
 * /cghub point set|del|list|tp
 * /cghub livre [joueur]
 * /cghub reload
 */
public final class HubCommand implements TabExecutor {
    private final CleanGangHub plugin;

    public HubCommand(CleanGangHub plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        if (a.length == 0) { help(s); return true; }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "world", "monde" -> world(s, a);
            case "point" -> point(s, a);
            case "livre", "book" -> book(s, a);
            case "reload" -> { plugin.reloadAll(); plugin.msg(s, "<green>Configuration rechargée."); }
            default -> help(s);
        }
        return true;
    }

    // ---------------------------------------------------------------- mondes
    private void world(CommandSender s, String[] a) {
        WorldManager wm = plugin.worlds();
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "list" -> {
                plugin.msg(s, "<gold>Mondes :");
                for (String ref : wm.knownWorlds()) {
                    World w = wm.resolve(ref);
                    s.sendMessage(Text.mm(w != null
                            ? " <green>● <white>" + ref + " <dark_gray>(" + w.getKey().asString() + ") <gray>"
                                + w.getPlayers().size() + " joueur(s)"
                            : " <red>○ <gray>" + ref + " <dark_gray>(non chargé)"));
                }
            }
            case "create", "load" -> {
                if (a.length < 3) {
                    plugin.msg(s, "<red>/cghub world " + sub + " <id> [" + String.join("|", WorldManager.TYPES) + "]");
                    return;
                }
                String id = a[2].toLowerCase(Locale.ROOT);
                if (!WorldManager.isValidId(id)) {
                    plugin.msg(s, "<red>Id invalide : minuscules, chiffres, _ - . uniquement.");
                    return;
                }
                String type = a.length > 3 ? a[3] : plugin.getConfig().getString("worlds." + id + ".type",
                        sub.equals("create") ? "void" : "normal");
                if (wm.resolve(id) != null) { plugin.msg(s, "<yellow>Ce monde est déjà chargé."); return; }
                if (sub.equals("load") && !wm.existsOnDisk(id)) {
                    plugin.msg(s, "<red>Rien trouvé pour '" + id + "'. Attendu : " + wm.keyFor(id).asString()
                            + " dans world/dimensions/, ou un dossier '" + id + "' (avec level.dat) à la racine.");
                    return;
                }
                plugin.msg(s, "<gray>Chargement de <white>" + id + "<gray>…");
                World w = sub.equals("create") ? wm.create(id, type) : wm.load(id, type, false);
                plugin.msg(s, w != null
                        ? "<green>Monde <white>" + id + "<green> prêt <dark_gray>(" + w.getKey().asString() + ")"
                        : "<red>Échec du chargement (voir la console).");
            }
            case "unload" -> {
                if (a.length < 3) { plugin.msg(s, "<red>/cghub world unload <id>"); return; }
                plugin.msg(s, wm.unload(a[2]) ? "<green>Monde déchargé." : "<red>Impossible (introuvable ou monde hub).");
            }
            case "tp" -> {
                if (a.length < 3) { plugin.msg(s, "<red>/cghub world tp <id> [joueur]"); return; }
                World w = wm.resolve(a[2]);
                Player target = a.length > 3 ? Bukkit.getPlayerExact(a[3]) : (s instanceof Player p ? p : null);
                if (w == null || target == null) { plugin.msg(s, "<red>Monde ou joueur introuvable."); return; }
                plugin.teleporter().teleport(target, w.getSpawnLocation().add(0.5, 0, 0.5), "<white>" + wm.label(w));
            }
            case "setspawn" -> {
                if (!(s instanceof Player p)) return;
                p.getWorld().setSpawnLocation(p.getLocation());
                plugin.msg(s, "<green>Spawn de <white>" + wm.label(p.getWorld()) + "<green> défini ici.");
            }
            default -> help(s);
        }
    }

    // ---------------------------------------------------------------- points
    private void point(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "list" -> {
                plugin.msg(s, "<gold>Points de la carte :");
                for (WarpPoint p : plugin.points().all()) {
                    s.sendMessage(Text.mm(" <yellow>" + p.id() + " <gray>case " + p.slot()
                            + " → " + p.world() + (p.available() ? "" : " <red>(non chargé)<gray>") + " " + (int) p.x() + " " + (int) p.y() + " " + (int) p.z()));
                }
            }
            case "set" -> {
                if (!(s instanceof Player p)) return;
                if (a.length < 4) { plugin.msg(s, "<red>/cghub point set <id> <case> [icône]"); return; }
                int slot;
                try { slot = Integer.parseInt(a[3]); } catch (NumberFormatException e) { plugin.msg(s, "<red>Case invalide."); return; }
                Material held = p.getInventory().getItemInMainHand().getType();
                Material icon = a.length > 4 ? Items.material(a[4], Material.ENDER_PEARL)
                        : (held.isAir() ? Material.ENDER_PEARL : held);
                WarpPoint old = plugin.points().get(a[2]);
                String name = old != null ? old.name() : "<yellow><bold>" + a[2];
                List<String> lore = old != null ? old.lore() : List.of();
                plugin.points().put(WarpPoint.at(a[2], name, icon, slot, lore, p.getLocation()));
                plugin.msg(s, "<green>Point <white>" + a[2] + "<green> posé en case " + slot
                        + ". <gray>(nom/description modifiables dans points.yml)");
            }
            case "del" -> {
                if (a.length < 3) { plugin.msg(s, "<red>/cghub point del <id>"); return; }
                plugin.msg(s, plugin.points().remove(a[2]) ? "<green>Point supprimé." : "<red>Point introuvable.");
            }
            case "tp" -> {
                if (!(s instanceof Player p) || a.length < 3) return;
                WarpPoint pt = plugin.points().get(a[2]);
                if (pt == null) { plugin.msg(s, "<red>Point introuvable."); return; }
                plugin.teleporter().teleport(p, pt.location(), pt.name());
            }
            default -> help(s);
        }
    }

    // ---------------------------------------------------------------- livre
    private void book(CommandSender s, String[] a) {
        Player target = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player p ? p : null);
        if (target == null) { plugin.msg(s, "<red>Joueur introuvable."); return; }
        target.getInventory().addItem(plugin.mapBook().create());
        plugin.msg(s, "<green>Livre-carte donné à " + target.getName() + ".");
    }

    private void help(CommandSender s) {
        plugin.msg(s, "<gold>Commandes :");
        for (String l : List.of(
                "/cghub world list | create <id> [type] | load <id> [type] | unload <id> | tp <id> [joueur] | setspawn",
                "/cghub point list | set <id> <case> [icône] | del <id> | tp <id>",
                "/cghub livre [joueur]",
                "/cghub reload")) {
            s.sendMessage(Text.mm(" <gray>" + l));
        }
    }

    // ---------------------------------------------------------------- tab
    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        List<String> opts = new ArrayList<>();
        if (a.length == 1) opts = List.of("world", "point", "livre", "reload");
        else if (a.length == 2 && a[0].equalsIgnoreCase("world")) opts = List.of("list", "create", "load", "unload", "tp", "setspawn");
        else if (a.length == 2 && a[0].equalsIgnoreCase("point")) opts = List.of("list", "set", "del", "tp");
        else if (a.length == 2 && a[0].equalsIgnoreCase("livre")) opts = players();
        else if (a.length == 3 && a[0].equalsIgnoreCase("world")) opts = plugin.worlds().knownWorlds();
        else if (a.length == 3 && a[0].equalsIgnoreCase("point")) opts = plugin.points().all().stream().map(WarpPoint::id).toList();
        else if (a.length == 4 && a[0].equalsIgnoreCase("world") && (a[1].equalsIgnoreCase("create") || a[1].equalsIgnoreCase("load"))) opts = WorldManager.TYPES;
        else if (a.length == 4 && a[0].equalsIgnoreCase("world") && a[1].equalsIgnoreCase("tp")) opts = players();
        else if (a.length == 5 && a[0].equalsIgnoreCase("point") && a[1].equalsIgnoreCase("set")) {
            String pre = a[4].toUpperCase(Locale.ROOT);
            opts = java.util.Arrays.stream(Material.values()).filter(Material::isItem).map(Material::name)
                    .filter(n -> n.startsWith(pre)).limit(30).toList();
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        return opts.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(last)).collect(Collectors.toList());
    }

    private List<String> players() {
        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
    }
}
