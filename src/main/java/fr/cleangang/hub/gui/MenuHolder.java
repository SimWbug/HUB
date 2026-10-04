package fr.cleangang.hub.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/** Identifie nos menus + garde l'état (actions par case, animation en cours, tâche de refresh). */
public final class MenuHolder implements InventoryHolder {
    public enum Type { MAP, GAMES }

    private final Type type;
    private final Map<Integer, String> actions = new HashMap<>();
    private Inventory inventory;
    private BukkitTask task;
    private CloudAnimation animation;

    public MenuHolder(Type type) { this.type = type; }

    public Type type() { return type; }
    public Map<Integer, String> actions() { return actions; }

    public void setInventory(Inventory inv) { this.inventory = inv; }
    @Override public @NotNull Inventory getInventory() { return inventory; }

    public void setTask(BukkitTask task) { this.task = task; }
    public void cancelTask() {
        if (task != null) { task.cancel(); task = null; }
    }

    public void setAnimation(CloudAnimation a) { this.animation = a; }
    public CloudAnimation animation() { return animation; }
    public boolean isAnimating() { return animation != null && !animation.isDone(); }
}
