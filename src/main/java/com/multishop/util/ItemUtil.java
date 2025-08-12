package com.multishop.util;

import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class ItemUtil {
    private ItemUtil(){}

    public static ItemStack withMeta(ItemStack base, String name, List<String> lore){
        ItemMeta meta = base.getItemMeta();
        if (name != null) meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        if (lore != null && !lore.isEmpty()){
            List<String> ll = new ArrayList<>();
            for (String s : lore) ll.add(ChatColor.translateAlternateColorCodes('&', s));
            meta.setLore(ll);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        base.setItemMeta(meta);
        return base;
    }
}
