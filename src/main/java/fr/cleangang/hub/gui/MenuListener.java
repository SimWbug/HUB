package fr.cleangang.hub.gui;

import fr.cleangang.hub.CleanGangHub;
import fr.cleangang.hub.map.WarpPoint;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {
    private final CleanGangHub plugin;

    public MenuListener(CleanGangHub plugin) { this.plugin = plugin; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof MenuHolder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;

        if (h.isAnimating()) {           // clic pendant les nuages = on saute l'animation
            h.animation().finish();
            return;
        }

        String action = h.actions().get(e.getRawSlot());
        if (action == null) return;

        if (action.startsWith("point:")) {
            WarpPoint pt = plugin.points().get(action.substring(6));
            if (pt != null) plugin.teleporter().teleport(p, pt.location(), pt.name());
        } else if (action.startsWith("game:")) {
            String id = action.substring(5);
            plugin.teleporter().teleport(p, plugin.gamesMenu().destination(id), plugin.gamesMenu().name(id));
        } else if (action.equals("games")) {
            plugin.gamesMenu().open(p);
        } else if (action.equals("map")) {
            plugin.mapMenu().open(p);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MenuHolder) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof MenuHolder h) {
            h.cancelTask();
            if (h.animation() != null && !h.animation().isDone()) {
                try { h.animation().cancel(); } catch (IllegalStateException ignored) { }
            }
        }
    }
}
