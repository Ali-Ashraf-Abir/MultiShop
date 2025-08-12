package com.multishop.model;

import com.multishop.config.ShopItemDef;
import org.bukkit.inventory.ItemStack;

public class ShopItemLive {
    public final ShopItemDef def;
    public final ItemStack displayIcon;

    public boolean available;    // rolled true this cycle?
    public int stockRemaining;   // global remaining stock this cycle

    public ShopItemLive(ShopItemDef def, ItemStack icon){
        this.def = def;
        this.displayIcon = icon;
    }
}
