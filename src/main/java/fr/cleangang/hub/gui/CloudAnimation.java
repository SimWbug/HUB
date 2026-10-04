package fr.cleangang.hub.gui;

import fr.cleangang.hub.CleanGangHub;
import fr.cleangang.hub.util.Items;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Nuages qui recouvrent la carte puis se dissipent.
 * - chaque case a une "étape de révélation" (distance au centre + bruit aléatoire => bord irrégulier)
 * - les nuages encore présents dérivent lentement vers la droite
 * - juste avant d'être révélée, une case passe en "nuage fin" (vitre transparente)
 * Un clic pendant l'animation la termine immédiatement.
 */
public final class CloudAnimation extends BukkitRunnable {
    private final CleanGangHub plugin;
    private final Player player;
    private final MenuHolder holder;
    private final ItemStack[] target;
    private final int size;
    private final int[] revealStep;
    private final ItemStack[][] pattern;
    private final ItemStack thin;
    private final long period;
    private final int lastStep;
    private int step = 0;
    private boolean done = false;

    public CloudAnimation(CleanGangHub plugin, Player player, MenuHolder holder, ItemStack[] target, int rows) {
        this.plugin = plugin;
        this.player = player;
        this.holder = holder;
        this.target = target;
        this.size = rows * 9;

        FileConfiguration c = plugin.getConfig();
        Random rnd = ThreadLocalRandom.current();

        List<ItemStack> clouds = new ArrayList<>();
        for (String s : c.getStringList("map.clouds.materials")) {
            Material m = Items.material(s, null);
            if (m != null) clouds.add(Items.filler(m));
        }
        if (clouds.isEmpty()) clouds.add(Items.filler(Material.WHITE_STAINED_GLASS_PANE));
        this.thin = Items.filler(Items.material(c.getString("map.clouds.thin"), Material.GLASS_PANE));
        this.period = Math.max(1, c.getLong("map.clouds.tick-interval", 2));

        this.pattern = new ItemStack[rows][9];
        for (int r = 0; r < rows; r++)
            for (int col = 0; col < 9; col++)
                pattern[r][col] = clouds.get(rnd.nextInt(clouds.size()));

        double spread = c.getDouble("map.clouds.spread", 1.4);
        double randomness = c.getDouble("map.clouds.randomness", 2.5);
        String mode = c.getString("map.clouds.mode", "center").toLowerCase(Locale.ROOT);
        double cr = (rows - 1) / 2.0, cc = 4.0;

        this.revealStep = new int[size];
        int max = 0;
        for (int i = 0; i < size; i++) {
            int r = i / 9, col = i % 9;
            double d = switch (mode) {
                case "left" -> col;
                case "random" -> rnd.nextDouble() * 6;
                default -> Math.hypot(r - cr, col - cc);
            };
            revealStep[i] = 2 + (int) Math.round(d * spread + rnd.nextDouble() * randomness);
            max = Math.max(max, revealStep[i]);
        }
        this.lastStep = max;
    }

    /** Recouvre tout de nuages (à appeler AVANT d'ouvrir l'inventaire). */
    public void prepare() {
        Inventory inv = holder.getInventory();
        for (int i = 0; i < size; i++) inv.setItem(i, cloudAt(i));
        holder.setAnimation(this);
    }

    public void start() {
        runTaskTimer(plugin, 3L, period);
    }

    private ItemStack cloudAt(int i) {
        int r = i / 9, col = i % 9;
        int drift = step / 3; // dérive lente vers la droite
        return pattern[r][Math.floorMod(col - drift, 9)];
    }

    @Override
    public void run() {
        if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder() != holder) {
            done = true;
            cancel();
            return;
        }
        Inventory inv = holder.getInventory();
        int revealed = 0;
        for (int i = 0; i < size; i++) {
            int rs = revealStep[i];
            if (rs < step) continue;
            if (rs == step) { inv.setItem(i, target[i]); revealed++; }
            else if (rs == step + 1) inv.setItem(i, thin);
            else inv.setItem(i, cloudAt(i));
        }
        if (revealed > 0 && step % 2 == 0) {
            player.playSound(player.getLocation(), Sound.BLOCK_POWDER_SNOW_STEP, 0.35f,
                    1.2f + (float) step / Math.max(1, lastStep) * 0.6f);
        }
        if (step >= lastStep) {
            finish();
            return;
        }
        step++;
    }

    /** Révèle toute la carte d'un coup. */
    public void finish() {
        if (done) return;
        done = true;
        try { cancel(); } catch (IllegalStateException ignored) { }
        holder.getInventory().setContents(target);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 0.8f);
    }

    public boolean isDone() { return done; }
}
