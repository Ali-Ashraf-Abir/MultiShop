package com.multishop.service;

import com.multishop.MultiShopPlugin;
import com.multishop.model.ShopItemLive;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class QuantityPromptService implements Listener {

    private final MultiShopPlugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public QuantityPromptService(MultiShopPlugin plugin){
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean isPrompting(UUID id){ return pending.containsKey(id); }

    public void startPrompt(Player p, String shopId, String itemId){
        // Replace any existing prompt
        cancelPrompt(p.getUniqueId(), false);

        // Verify item is still present
        var state = plugin.shopManager().liveState(shopId);
        if (state == null || !state.indexById.containsKey(itemId)){
            p.sendMessage(ChatColor.RED + "That item is no longer available.");
            return;
        }

        Pending pd = new Pending(shopId, itemId);
        pending.put(p.getUniqueId(), pd);

        p.sendMessage(ChatColor.GOLD + "Enter amount to buy (or type 'cancel'). You have 30s.");
        p.showTitle(net.kyori.adventure.title.Title.title(
                Component.text(" "),
                Component.text("Type amount in chat…")
        ));

        // Auto-timeout after 30s
        pd.timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pending.remove(p.getUniqueId()) != null) {
                p.sendMessage(ChatColor.YELLOW + "Purchase input timed out.");
            }
        }, 20L * 30);
    }

    private void cancelPrompt(UUID id, boolean tell){
        Pending pd = pending.remove(id);
        if (pd != null && pd.timeoutTask != null) {
            pd.timeoutTask.cancel();
        }
        if (tell) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.sendMessage(ChatColor.YELLOW + "Cancelled.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e){
        cancelPrompt(e.getPlayer().getUniqueId(), false);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent e){
        UUID id = e.getPlayer().getUniqueId();
        Pending pd = pending.get(id);
        if (pd == null) return;

        // This message is for us only
        e.setCancelled(true);

        String msg = e.getMessage().trim();
        if (msg.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> cancelPrompt(id, true));
            return;
        }

        int requested;
        try {
            requested = Integer.parseInt(msg);
        } catch (NumberFormatException ex) {
            e.getPlayer().sendMessage(ChatColor.RED + "Please type a whole number, or 'cancel'.");
            return;
        }
        if (requested <= 0){
            e.getPlayer().sendMessage(ChatColor.RED + "Amount must be at least 1.");
            return;
        }

        // Run the purchase on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = e.getPlayer();
            var state = plugin.shopManager().liveState(pd.shopId);
            if (state == null || !state.indexById.containsKey(pd.itemId)){
                p.sendMessage(ChatColor.RED + "That item is no longer available.");
                cancelPrompt(id, false);
                return;
            }

            ShopItemLive li = state.visible.get(state.indexById.get(pd.itemId));

            // Compute maximum allowed (mirrors ShopGUI logic)
            var bought = state.perPlayerBought.getOrDefault(p.getUniqueId(), Map.of());
            int have = bought.getOrDefault(li.def.id, 0);
            int leftPerPlayer = Math.max(0, li.def.maxPerPlayer - have);
            int stockLeft = li.def.unlimitedStock ? Integer.MAX_VALUE : li.stockRemaining;

            Economy econ = plugin.economy();
            int affordable = (int)Math.max(0, Math.floor(econ.getBalance(p) / Math.max(1, li.def.price)));

            int maxPossible = Math.min(Math.min(leftPerPlayer, stockLeft), affordable);
            if (maxPossible <= 0){
                if (leftPerPlayer <= 0) p.sendMessage(ChatColor.RED + "Purchase limit reached for this cycle.");
                else if (stockLeft <= 0) p.sendMessage(ChatColor.RED + "Out of stock.");
                else p.sendMessage(ChatColor.RED + "Not enough balance.");
                cancelPrompt(id, false);
                return;
            }

            int qty = Math.min(requested, maxPossible);
            // Reuse ShopGUI purchase API
            boolean ok = new com.multishop.gui.ShopGUI(plugin).purchase(p, pd.shopId, li, qty);
            if (ok) {
                cancelPrompt(id, false);
            } else {
                // ShopGUI already messaged & refunded if needed
                cancelPrompt(id, false);
            }
        });
    }

    private static class Pending {
        final String shopId;
        final String itemId;
        org.bukkit.scheduler.BukkitTask timeoutTask;
        Pending(String shopId, String itemId){
            this.shopId = shopId;
            this.itemId = itemId;
        }
    }
}
