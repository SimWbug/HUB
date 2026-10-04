package fr.cleangang.hub.gui;

import fr.cleangang.hub.CleanGangHub;
import fr.cleangang.hub.map.WarpPoint;
import fr.cleangang.hub.util.Items;
import fr.cleangang.hub.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MapMenu {
    private final CleanGangHub plugin;

    public MapMenu(CleanGangHub plugin) { this.plugin = plugin; }

    public void open(Player player) {
        FileConfiguration c = plugin.getConfig();
        int rows = Math.max(1, Math.min(6, c.getInt("map.rows", 6)));

        MenuHolder holder = new MenuHolder(MenuHolder.Type.MAP);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Text.mm(c.getString("map.title", "Carte")));
        holder.setInventory(inv);
        ItemStack[] content = build(rows, holder);

        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);

        if (!c.getBoolean("map.clouds.enabled", true)) {
            inv.setContents(content);
            player.openInventory(inv);
            return;
        }
        CloudAnimation anim = new CloudAnimation(plugin, player, holder, content, rows);
        anim.prepare();
        player.openInventory(inv);
        anim.start();
    }

    private ItemStack[] build(int rows, MenuHolder holder) {
        FileConfiguration c = plugin.getConfig();
        int size = rows * 9;
        ItemStack[] content = new ItemStack[size];

        // 1) le dessin de la carte
        List<String> layout = c.getStringList("map.layout");
        ConfigurationSection legend = c.getConfigurationSection("map.legend");
        Material bg = Items.material(c.getString("map.background"), Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        Map<Character, ItemStack> cache = new HashMap<>();
        for (int i = 0; i < size; i++) {
            int r = i / 9, col = i % 9;
            char ch = (r < layout.size() && col < layout.get(r).length()) ? layout.get(r).charAt(col) : ' ';
            ItemStack tile = cache.computeIfAbsent(ch, k -> {
                String mat = (legend != null && k != ' ') ? legend.getString(String.valueOf(k)) : null;
                return Items.filler(Items.material(mat, bg));
            });
            content[i] = tile.clone();
        }

        // 2) les points de téléportation
        for (WarpPoint pt : plugin.points().all()) {
            if (pt.slot() < 0 || pt.slot() >= size) continue;
            boolean ok = pt.available();
            List<String> lore = new ArrayList<>(pt.lore());
            lore.add("");
            if (ok) {
                int n = Bukkit.getWorld(pt.world()).getPlayers().size();
                lore.add("<dark_gray>Monde : <gray>" + pt.world() + " <dark_gray>• <gray>" + n + " joueur(s)");
                lore.add("<green>▶ Clic pour t'y rendre");
            } else {
                lore.add("<red>✖ Monde non chargé");
            }
            content[pt.slot()] = Items.icon(ok ? pt.icon() : Material.BARRIER, pt.name(), lore, ok);
            holder.actions().put(pt.slot(), "point:" + pt.id());
        }

        // 3) bouton vers le menu mini-jeux
        if (c.getBoolean("map.games-button.enabled", true)) {
            int gs = c.getInt("map.games-button.slot", size - 1);
            if (gs >= 0 && gs < size) {
                content[gs] = Items.icon(
                        Items.material(c.getString("map.games-button.icon"), Material.COMPASS),
                        c.getString("map.games-button.name", "<gold>Mini-jeux"),
                        c.getStringList("map.games-button.lore"), true);
                holder.actions().put(gs, "games");
            }
        }
        return content;
    }
}
