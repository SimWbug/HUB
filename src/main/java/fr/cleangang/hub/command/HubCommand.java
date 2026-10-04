package fr.cleangang.hub.command;

import fr.cleangang.hub.CleanGangHub;
import fr.cleangang.hub.map.WarpPoint;
import fr.cleangang.hub.util.Items;
import fr.cleangang.hub.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** /cghub point|livre|reload — la carte et le livre (les mondes sont dans /world). */
public final class HubCommand implements TabExecutor {
    private final CleanGangHub plugin;

    public HubCommand(CleanGangHub plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        if (a.length == 0) { help(s); return true; }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "point" -> point(s, a);
            case "livre", "book" -> book(s, a);
            case "reload" -> { plugin.reloadAll(); plugin.msg(s, "<green>Configuration rechargée."); }
            default -> help(s);
        }
        return true;
    }

    private void point(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "list" -> {
                plugin.msg(s, "<gold>Points de la carte :");
                for (WarpPoint p : plugin.points().all()) {
                    s.sendMessage(Text.mm(" <yellow>" + p.id() + " <gray>case " + p.slot() + " → " + p.world()
                            + " " + (int) p.x() + " " + (int) p.y() + " " + (int) p.z()
                            + (p.available() ? "" : " <red>(monde non chargé)")));
                }
            }
            case "set" -> {
                if (!(s instanceof Player p)) return;
                if (a.length < 4) { plugin.msg(s, "<red>/cghub point set <id> <case> [icône]"); return; }
                int slot;
                try { slot = Integer.parseInt(a[3]); } catch (NumberFormatException e) { plugin.msg(s, "<red>Case invalide."); return; }
                Material held = p.getInventory().getItemInMainHand().getType();
                Material icon = a.length > 4 ? Items.material(a[4], Material.ENDER_PEARL) : (held.isAir() ? Material.ENDER_PEARL : held);
                WarpPoint old = plugin.points().get(a[2]);
                String name = old != null ? old.name() : "<yellow><bold>" + a[2];
                List<String> lore = old != null ? old.lore() : List.of();
                plugin.points().put(WarpPoint.at(a[2], name, icon, slot, lore, p.getLocation()));
                plugin.msg(s, "<green>Point <white>" + a[2] + "<green> posé en case " + slot + ". <gray>(nom/description dans points.yml)");
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

    private void book(CommandSender s, String[] a) {
        Player target = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player p ? p : null);
        if (target == null) { plugin.msg(s, "<red>Joueur introuvable."); return; }
        target.getInventory().addItem(plugin.mapBook().create());
        plugin.msg(s, "<green>Livre-carte donné à " + target.getName() + ".");
    }

    private void help(CommandSender s) {
        plugin.msg(s, "<gold>Commandes /cghub :");
        for (String l : List.of(
                "/cghub point list | set <id> <case> [icône] | del <id> | tp <id>",
                "/cghub livre [joueur]",
                "/cghub reload",
                "<dark_gray>Mondes : /world help")) {
            s.sendMessage(Text.mm(" <gray>" + l));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command c, @NotNull String l, String @NotNull [] a) {
        List<String> o = new ArrayList<>();
        if (a.length == 1) o = List.of("point", "livre", "reload");
        else if (a.length == 2 && a[0].equalsIgnoreCase("point")) o = List.of("list", "set", "del", "tp");
        else if (a.length == 2 && a[0].equalsIgnoreCase("livre")) o = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        else if (a.length == 3 && a[0].equalsIgnoreCase("point")) o = plugin.points().all().stream().map(WarpPoint::id).toList();
        else if (a.length == 5 && a[0].equalsIgnoreCase("point") && a[1].equalsIgnoreCase("set")) {
            String pre = a[4].toUpperCase(Locale.ROOT);
            o = Arrays.stream(Material.values()).filter(Material::isItem).map(Material::name).filter(n -> n.startsWith(pre)).limit(30).toList();
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        return o.stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(last)).toList();
    }
}
