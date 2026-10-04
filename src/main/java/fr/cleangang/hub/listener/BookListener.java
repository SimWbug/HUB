package fr.cleangang.hub.listener;

import fr.cleangang.hub.CleanGangHub;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class BookListener implements Listener {
    private final CleanGangHub plugin;

    public BookListener(CleanGangHub plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!plugin.mapBook().isBook(e.getItem())) return;
        e.setCancelled(true);
        plugin.mapMenu().open(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (plugin.getConfig().getBoolean("book.give-on-join", true)) plugin.mapBook().give(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        if (!plugin.getConfig().getBoolean("book.give-on-join", true)) return;
        Bukkit.getScheduler().runTask(plugin, () -> plugin.mapBook().give(e.getPlayer()));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (plugin.getConfig().getBoolean("book.locked", true)
                && plugin.mapBook().isBook(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        if (plugin.getConfig().getBoolean("book.locked", true)) {
            e.getDrops().removeIf(plugin.mapBook()::isBook);
        }
    }
}
