package fr.cleangang.hub.util;

import fr.cleangang.hub.CleanGangHub;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;

/** Téléportation "à travers les nuages" : titre blanc + aveuglement court, puis TP. */
public final class Teleporter {
    private final CleanGangHub plugin;

    public Teleporter(CleanGangHub plugin) { this.plugin = plugin; }

    public void teleport(Player p, Location target, String labelMiniMessage) {
        if (target == null || target.getWorld() == null) {
            plugin.msg(p, "<red>Cette destination n'est pas disponible (monde non chargé).");
            return;
        }
        p.closeInventory();

        int delay = Math.max(0, plugin.getConfig().getInt("teleport.delay-ticks", 20));
        Component title = Text.mm(plugin.getConfig().getString("teleport.title", "<white>☁ ☁ ☁"));
        Component sub = Text.mm(labelMiniMessage);

        p.showTitle(Title.title(title, sub, Title.Times.times(
                Duration.ofMillis(250),
                Duration.ofMillis(delay * 50L + 300),
                Duration.ofMillis(600))));
        if (delay > 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, delay + 15, 0, false, false, false));
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.8f, 1.3f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            p.teleportAsync(target).thenAccept(ok -> {
                if (ok) p.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
            });
        }, delay);
    }
}
