package com.multishop.gui;

import com.multishop.MultiShopPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.inventory.ItemStack;

public class TokenGuardListener implements Listener {

    public TokenGuardListener(MultiShopPlugin plugin){
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent e){
        Item ent = e.getEntity();
        ItemStack stack = ent.getItemStack();
        if (stack == null || stack.getType() != Material.PAPER) return;
        if (!stack.hasItemMeta() || !stack.getItemMeta().hasDisplayName()) return;
        String name = stack.getItemMeta().getDisplayName();
        // Our price token names look like: ChatColor.YELLOW + "$" + price
        if (name != null && name.startsWith(ChatColor.YELLOW + "$")){
            // Prevent the token from ever appearing on the ground
            e.setCancelled(true);
        }
    }
}
