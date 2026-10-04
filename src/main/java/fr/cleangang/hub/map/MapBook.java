package fr.cleangang.hub.map;

import fr.cleangang.hub.CleanGangHub;
import fr.cleangang.hub.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

/** Le livre qui ouvre la carte. Reconnu grâce à un tag PDC (pas par son nom). */
public final class MapBook {
    private final CleanGangHub plugin;
    private final NamespacedKey key;

    public MapBook(CleanGangHub plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "map_book");
    }

    public ItemStack create() {
        ItemStack it = new ItemStack(Material.BOOK);
        it.editMeta(meta -> {
            meta.displayName(Text.mm(plugin.getConfig().getString("book.name", "Carte du monde")));
            meta.lore(Text.mm(plugin.getConfig().getStringList("book.lore")));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            String model = plugin.getConfig().getString("book.item-model", "");
            if (model != null && !model.isBlank()) {
                NamespacedKey mk = NamespacedKey.fromString(model);
                if (mk != null) meta.setItemModel(mk);
            }
        });
        return it;
    }

    public boolean isBook(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Donne le livre s'il ne l'a pas déjà, dans le slot configuré si possible. */
    public void give(Player p) {
        PlayerInventory inv = p.getInventory();
        for (ItemStack it : inv.getContents()) if (isBook(it)) return;
        int slot = plugin.getConfig().getInt("book.slot", 8);
        if (slot >= 0 && slot <= 8 && (inv.getItem(slot) == null || inv.getItem(slot).getType().isAir())) {
            inv.setItem(slot, create());
        } else {
            inv.addItem(create());
        }
    }
}
