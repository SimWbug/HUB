package fr.cleangang.hub.world.listener;

import fr.cleangang.hub.CleanGangHub;
import fr.cleangang.hub.world.WorldFlag;
import fr.cleangang.hub.world.WorldService;
import io.papermc.paper.advancement.AdvancementDisplay;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

import java.util.EnumSet;
import java.util.Set;

/** Applique les flags de monde : spawns, redstone, météo, faim, PvP, succès. */
public final class FlagListener implements Listener {
    /**
     * Apparitions "naturelles" bloquées par les flags spawn-*.
     * Les spawns de plugins (CUSTOM, ex. le poulet de Find The Poulet), de commandes et d'oeufs restent permis.
     */
    private static final Set<SpawnReason> NATURAL = EnumSet.of(
            SpawnReason.NATURAL, SpawnReason.SPAWNER, SpawnReason.TRIAL_SPAWNER, SpawnReason.PATROL,
            SpawnReason.RAID, SpawnReason.REINFORCEMENTS, SpawnReason.JOCKEY, SpawnReason.MOUNT,
            SpawnReason.TRAP, SpawnReason.VILLAGE_INVASION, SpawnReason.NETHER_PORTAL);

    private final WorldService worlds;

    public FlagListener(CleanGangHub plugin) { this.worlds = plugin.worlds(); }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!NATURAL.contains(e.getSpawnReason())) return;
        var w = e.getLocation().getWorld();
        if (!worlds.bool(w, WorldFlag.SPAWN_ENTITIES)
                || (e.getEntity() instanceof Animals && !worlds.bool(w, WorldFlag.SPAWN_ANIMALS))
                || (e.getEntity() instanceof Enemy && !worlds.bool(w, WorldFlag.SPAWN_MONSTERS))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onRedstone(BlockRedstoneEvent e) {
        if (!worlds.bool(e.getBlock().getWorld(), WorldFlag.REDSTONE)) e.setNewCurrent(0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeather(WeatherChangeEvent e) {
        if (e.toWeatherState() && !worlds.bool(e.getWorld(), WorldFlag.WEATHER)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onThunder(ThunderChangeEvent e) {
        if (e.toThunderState() && !worlds.bool(e.getWorld(), WorldFlag.WEATHER)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (e.getFoodLevel() < p.getFoodLevel() && !worlds.bool(p.getWorld(), WorldFlag.HUNGER)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) attacker = p;
        if (attacker == null || attacker.equals(victim)) return;
        if (!worlds.bool(victim.getWorld(), WorldFlag.PVP)) e.setCancelled(true);
    }

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        if (worlds.bool(e.getPlayer().getWorld(), WorldFlag.ADVANCEMENTS)) return;
        AdvancementDisplay display = e.getAdvancement().getDisplay();
        if (display == null) return; // recettes & succès cachés : on ne touche pas
        e.message(null);
        AdvancementProgress progress = e.getPlayer().getAdvancementProgress(e.getAdvancement());
        for (String c : progress.getAwardedCriteria()) progress.revokeCriteria(c);
    }
}
