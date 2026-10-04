package fr.cleangang.hub.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class Items {
    private Items() {}

    public static Material material(String name, Material def) {
        if (name == null || name.isBlank()) return def;
        Material m = Material.matchMaterial(name.trim());
        return (m == null || !m.isItem() || m.isAir()) ? def : m;
    }

    /** Case décorative sans texte (fond de carte, nuages...). */
    public static ItemStack filler(Material m) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> meta.displayName(Component.text(" ")));
        return it;
    }

    public static ItemStack icon(Material m, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(m);
        it.editMeta(meta -> {
            meta.displayName(Text.mm(name));
            meta.lore(Text.mm(lore));
            meta.addItemFlags(ItemFlag.values());
            if (glow) meta.setEnchantmentGlintOverride(true);
        });
        return it;
    }
}
