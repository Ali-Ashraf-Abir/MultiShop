package com.multishop.gui;

import com.multishop.MultiShopPlugin;
import com.multishop.model.ShopItemLive;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;

import java.util.*;

public class GuiListener implements Listener {

    private static final Map<UUID, WindowCtx> open = new HashMap<>();
    private static final Map<UUID, Integer> primingTasks = new HashMap<>();

    public GuiListener(MultiShopPlugin plugin){
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static void bindWindow(UUID player, String shopId, List<ShopItemLive> items){
        open.put(player, new WindowCtx(shopId, items));
        startPriming(player);
    }

    private static void startPriming(UUID playerId){
        stopPriming(playerId);
        long period = Math.max(1L, MultiShopPlugin.inst().getConfig().getLong("performance.priming-period-ticks", 5));
        int taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(MultiShopPlugin.inst(), () -> {
            Player p = Bukkit.getPlayer(playerId);
            if (p == null || !p.isOnline()) return;
            InventoryView view = p.getOpenInventory();
            if (view == null || !(view.getTopInventory() instanceof MerchantInventory mi)) return;
            if (!open.containsKey(playerId)) return;

            int idx = mi.getSelectedRecipeIndex();
            WindowCtx ctx = open.get(playerId);
            if (ctx == null || idx < 0 || idx >= ctx.items.size()) return;

            ItemStack want = priceToken(ctx.items.get(idx).def.price);
            ItemStack cur0 = mi.getItem(0);
            if (!isOurToken(cur0, ctx.items.get(idx).def.price)) {
                mi.setItem(0, want);
            }
            if (mi.getItem(1) != null) {
                mi.setItem(1, null);
            }

            cleanPriceTokensFromPlayer(p);
        }, 1L, period);
        primingTasks.put(playerId, taskId);
    }

    private static void stopPriming(UUID playerId){
        Integer tid = primingTasks.remove(playerId);
        if (tid != null) Bukkit.getScheduler().cancelTask(tid);
    }

    public static ItemStack priceToken(int price){
        ItemStack paper = new ItemStack(Material.PAPER, 1);
        var meta = paper.getItemMeta();
        meta.setDisplayName(ChatColor.YELLOW + "$" + price);
        paper.setItemMeta(meta);
        return paper;
    }

    private static boolean isOurToken(ItemStack stack, int price){
        if (stack == null || stack.getType() != Material.PAPER) return false;
        if (!stack.hasItemMeta() || !stack.getItemMeta().hasDisplayName()) return false;
        return (ChatColor.YELLOW + "$" + price).equals(stack.getItemMeta().getDisplayName());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e){
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory() instanceof MerchantInventory mi)) return;
        if (!open.containsKey(p.getUniqueId())) return;

        // RESULT FIRST (so shift-click works)
        if (e.getSlotType() == InventoryType.SlotType.RESULT){
            e.setCancelled(true);

            WindowCtx ctx = open.get(p.getUniqueId());
            int idx = mi.getSelectedRecipeIndex();
            if (ctx == null || idx < 0 || idx >= ctx.items.size()) return;
            var li = ctx.items.get(idx);

            if (e.getClick().isRightClick()){
                p.closeInventory();
                MultiShopPlugin.inst().quantityPrompt().startPrompt(p, ctx.shopId, li.def.id);
            } else {
                int requested = e.getClick().isShiftClick() ? Integer.MAX_VALUE : 1;
                boolean ok = new com.multishop.gui.ShopGUI(MultiShopPlugin.inst()).purchase(p, ctx.shopId, li, requested);
                if (ok) p.updateInventory();
            }

            mi.setItem(0, null);
            mi.setItem(1, null);
            cleanPriceTokensFromPlayer(p);
            return;
        }

        // block shift moves for non-result
        if (e.getClick().isShiftClick()){
            e.setCancelled(true);
            return;
        }
        if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY){
            e.setCancelled(true);
            return;
        }
        // block clicks in player inv while merchant open
        if (e.getClickedInventory() != null && e.getClickedInventory().getType() != InventoryType.MERCHANT){
            e.setCancelled(true);
            return;
        }
        // block ingredient slots
        if (e.getClickedInventory() != null
                && e.getClickedInventory().getType() == InventoryType.MERCHANT
                && (e.getSlot() == 0 || e.getSlot() == 1)) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(MultiShopPlugin.inst(), () -> {
                WindowCtx ctx = open.get(p.getUniqueId());
                if (ctx == null) return;
                int idx = mi.getSelectedRecipeIndex();
                if (idx < 0 || idx >= ctx.items.size()) return;
                mi.setItem(0, priceToken(ctx.items.get(idx).def.price));
                mi.setItem(1, null);
                cleanPriceTokensFromPlayer(p);
            });
            return;
        }

        // normal left panel clicks: allow selection change then prime next tick
        Bukkit.getScheduler().runTask(MultiShopPlugin.inst(), () -> {
            if (!p.isOnline()) return;
            Inventory top = p.getOpenInventory().getTopInventory();
            if (!(top instanceof MerchantInventory cur)) return;
            WindowCtx ctx = open.get(p.getUniqueId());
            if (ctx == null) return;

            int idx = cur.getSelectedRecipeIndex();
            if (idx < 0 || idx >= ctx.items.size()) return;
            cur.setItem(0, priceToken(ctx.items.get(idx).def.price));
            cur.setItem(1, null);
            cleanPriceTokensFromPlayer(p);
        });
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e){
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory() instanceof MerchantInventory)) return;
        if (!open.containsKey(p.getUniqueId())) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e){
        UUID id = e.getPlayer().getUniqueId();
        open.remove(id);
        stopPriming(id);

        if (e.getPlayer() instanceof Player p){
            cleanPriceTokensFromPlayer(p);
        }

        MultiShopPlugin.inst().actionBar().unwatch(id);
        MultiShopPlugin.inst().timerBar().show(id); // only shows if opted-in
    }

    private static void cleanPriceTokensFromPlayer(Player p){
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++){
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType() != Material.PAPER) continue;
            if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()){
                String name = it.getItemMeta().getDisplayName();
                if (name != null && name.startsWith(ChatColor.YELLOW + "$")){
                    inv.setItem(i, null);
                }
            }
        }
        // also clear cursor if player somehow grabbed it
        ItemStack cursor = p.getItemOnCursor();
        if (cursor != null && cursor.getType() == Material.PAPER
                && cursor.hasItemMeta() && cursor.getItemMeta().hasDisplayName()
                && cursor.getItemMeta().getDisplayName().startsWith(ChatColor.YELLOW + "$")){
            p.setItemOnCursor(null);
        }
    }

    private record WindowCtx(String shopId, List<ShopItemLive> items){}
}
