package fr.cleangang.hub.gui;

import fr.cleangang.hub.CleanGangHub;
import fr.cleangang.hub.map.WarpPoint;
import fr.cleangang.hub.util.Items;
import fr.cleangang.hub.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Menu des modes de jeu, rafraîchi chaque seconde (nombre de joueurs). */
public final class GamesMenu {
    private final CleanGangHub plugin;

    public GamesMenu(CleanGangHub plugin) { this.plugin = plugin; }

    public void open(Player p) {
        FileConfiguration c = plugin.getConfig();
        int rows = Math.max(1, Math.min(6, c.getInt("games-menu.rows", 3)));
        MenuHolder holder = new MenuHolder(MenuHolder.Type.GAMES);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Text.mm(c.getString("games-menu.title", "Mini-jeux")));
        holder.setInventory(inv);
        render(holder);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);

        holder.setTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline() || p.getOpenInventory().getTopInventory().getHolder() != holder) {
                holder.cancelTask();
                return;
            }
            render(holder);
        }, 20L, 20L));
    }

    private void render(MenuHolder h) {
        FileConfiguration c = plugin.getConfig();
        Inventory inv = h.getInventory();
        int size = inv.getSize();
        h.actions().clear();

        ItemStack filler = Items.filler(Items.material(c.getString("games-menu.filler"), Material.BLACK_STAINED_GLASS_PANE));
        ItemStack[] content = new ItemStack[size];
        for (int i = 0; i < size; i++) content[i] = filler;

        ConfigurationSection games = c.getConfigurationSection("games");
        if (games != null) {
            for (String id : games.getKeys(false)) {
                ConfigurationSection g = games.getConfigurationSection(id);
                if (g == null) continue;
                int slot = g.getInt("slot", -1);
                if (slot < 0 || slot >= size) continue;

                World w = plugin.worlds().resolve(g.getString("world", id));
                int players = w == null ? 0 : w.getPlayers().size();
                boolean open = destination(id) != null;

                List<String> lore = new ArrayList<>(g.getStringList("lore"));
                lore.add("");
                lore.add("<gray>Joueurs : <white>" + players);
                lore.add(open ? "<green>▶ Clic pour rejoindre" : "<red>✖ Fermé (monde non chargé)");

                ItemStack icon = Items.icon(open ? Items.material(g.getString("icon"), Material.PAPER) : Material.BARRIER,
                        g.getString("name", id), lore, players > 0);
                icon.setAmount(Math.max(1, Math.min(64, players)));
                content[slot] = icon;
                h.actions().put(slot, "game:" + id);
            }
        }

        int back = c.getInt("games-menu.back-button.slot", -1);
        if (back >= 0 && back < size) {
            content[back] = Items.icon(Items.material(c.getString("games-menu.back-button.icon"), Material.BOOK),
                    c.getString("games-menu.back-button.name", "<yellow>← Carte"), List.of(), false);
            h.actions().put(back, "map");
        }
        inv.setContents(content);
    }

    /** Point configuré (games.<id>.point) sinon spawn du monde. */
    public Location destination(String id) {
        ConfigurationSection g = plugin.getConfig().getConfigurationSection("games." + id);
        if (g == null) return null;
        WarpPoint pt = plugin.points().get(g.getString("point"));
        if (pt != null) return pt.location();
        World w = plugin.worlds().resolve(g.getString("world", id));
        return w == null ? null : w.getSpawnLocation().add(0.5, 0, 0.5);
    }

    public String name(String id) {
        return plugin.getConfig().getString("games." + id + ".name", id);
    }
}
