package com.multishop.gui;

import com.multishop.MultiShopPlugin;
import com.multishop.config.ShopDefinition;
import com.multishop.model.ShopItemLive;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ShopGUI {

    private final MultiShopPlugin plugin;

    public ShopGUI(MultiShopPlugin plugin){ this.plugin = plugin; }

    public void open(Player p, ShopDefinition def, List<ShopItemLive> items){
        Merchant merchant = Bukkit.createMerchant(ChatColor.translateAlternateColorCodes('&', def.displayName));
        List<MerchantRecipe> recipes = new ArrayList<>();

        var state = plugin.shopManager().liveState(def.id);
        var bought = state.perPlayerBought.getOrDefault(p.getUniqueId(), Map.of());

        for (ShopItemLive li : items){
            int have = bought.getOrDefault(li.def.id, 0);
            int leftPerPlayer = Math.max(0, li.def.maxPerPlayer - have);

            ItemStack shown = li.displayIcon.clone();
            var meta = shown.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(ChatColor.YELLOW + "Price: $" + li.def.price);
            lore.add(ChatColor.GRAY + "Per-player left: " + leftPerPlayer);
            lore.add(ChatColor.GRAY + "Stock left: " + (li.def.unlimitedStock ? "Unlimited" : li.stockRemaining));

            // Capacity hint (vanilla items only; custom is hard to know reliably)
            int capHint = ("VANILLA".equalsIgnoreCase(li.def.type))
                    ? capacityForVanilla(p.getInventory(), new ItemStack(li.displayIcon.getType()))
                    : emptySlots(p.getInventory());
            lore.add(ChatColor.DARK_GRAY + "Inv capacity: " + capHint);

            if (!li.available || (!li.def.unlimitedStock && li.stockRemaining <= 0)){
                lore.add(ChatColor.RED + "Not available this cycle");
            } else {
                lore.add(ChatColor.GREEN + "Click result to purchase");
                lore.add(ChatColor.DARK_GRAY + "(Shift-click: buy max, Right-click: exact)");
            }
            meta.setLore(lore);
            shown.setItemMeta(meta);

            // Villager UI needs an ingredient; inject a fake price token.
            MerchantRecipe recipe = new MerchantRecipe(shown, 999_999);
            recipe.setExperienceReward(false);
            recipe.setVillagerExperience(0);
            recipe.addIngredient(GuiListener.priceToken(li.def.price));
            recipes.add(recipe);
        }
        merchant.setRecipes(recipes);

        p.openMerchant(merchant, true);
        GuiListener.bindWindow(p.getUniqueId(), def.id, items);

        // prime immediately so the first selected trade shows the result icon right away
        Bukkit.getScheduler().runTask(MultiShopPlugin.inst(), () -> {
            var view = p.getOpenInventory();
            if (view == null || !(view.getTopInventory() instanceof org.bukkit.inventory.MerchantInventory mi)) return;
            if (items.isEmpty()) return;
            mi.setItem(0, GuiListener.priceToken(items.get(0).def.price));
            mi.setItem(1, null);
        });

        // hide bossbar while trading; action bar keeps ticking
        plugin.timerBar().hide(p.getUniqueId());
        plugin.actionBar().watch(p.getUniqueId());
    }

    // Backward compat
    public boolean purchase(Player p, String shopId, ShopItemLive li){
        return purchase(p, shopId, li, 1);
    }

    // Batch purchase (requested == MAX_VALUE means "buy max")
    public boolean purchase(Player p, String shopId, ShopItemLive li, int requested){
        if (plugin.shopManager().isBanned(shopId, p.getUniqueId())){
            p.sendMessage(ChatColor.RED + "You are banned from this shop.");
            return false;
        }
        var state = plugin.shopManager().liveState(shopId);
        if (state.paused){
            p.sendMessage(ChatColor.RED + "This shop is temporarily paused.");
            return false;
        }
        if (!li.available){
            p.sendMessage(ChatColor.RED + "Not available this cycle.");
            return false;
        }

        // Limits from stock/per-player/balance
        var bought = state.perPlayerBought.getOrDefault(p.getUniqueId(), Map.of());
        int have = bought.getOrDefault(li.def.id, 0);
        int leftPerPlayer = Math.max(0, li.def.maxPerPlayer - have);
        int stockLeft = li.def.unlimitedStock ? Integer.MAX_VALUE : li.stockRemaining;
        int affordable = (int)Math.max(0, Math.floor(plugin.economy().getBalance(p) / Math.max(1, li.def.price)));

        // NEW: inventory capacity constraint
        int invCapacity = ("VANILLA".equalsIgnoreCase(li.def.type))
                ? capacityForVanilla(p.getInventory(), new ItemStack(li.displayIcon.getType()))
                : emptySlots(p.getInventory()); // assume custom items require empty slots

        int maxPossible = Math.min(Math.min(leftPerPlayer, stockLeft), Math.min(affordable, invCapacity));
        if (maxPossible <= 0){
            if (leftPerPlayer <= 0) p.sendMessage(ChatColor.RED + "Purchase limit reached for this cycle.");
            else if (stockLeft <= 0) p.sendMessage(ChatColor.RED + "Out of stock.");
            else if (affordable <= 0) p.sendMessage(ChatColor.RED + "Not enough balance.");
            else p.sendMessage(ChatColor.RED + "Not enough inventory space.");
            return false;
        }

        int qty = Math.max(1, Math.min(requested, maxPossible));

        // withdraw total
        int totalCost = qty * li.def.price;
        EconomyResponse r = plugin.economy().withdrawPlayer(p, totalCost);
        if (!r.transactionSuccess()){
            p.sendMessage(ChatColor.RED + "Not enough balance.");
            return false;
        }

        // Give items
        boolean allOk = true;
        if ("CUSTOM".equalsIgnoreCase(li.def.type) && li.def.giveCommand != null){
            // For custom items, we already required empty slots; proceed to dispatch
            String base = li.def.giveCommand;
            String exec = li.def.executeAs != null ? li.def.executeAs.toUpperCase() : "CONSOLE";

            if (base.contains("%amount%")){
                String cmd = base.replace("%player%", p.getName()).replace("%amount%", String.valueOf(qty));
                allOk = dispatch(exec, p, cmd);
            } else {
                for (int i=0; i<qty; i++){
                    String cmd = base.replace("%player%", p.getName());
                    if (!dispatch(exec, p, cmd)){ allOk = false; break; }
                }
            }
        } else {
            // VANILLA: deliver safely with pre-checked capacity
            Material mat = li.displayIcon.getType();
            int maxStack = new ItemStack(mat).getMaxStackSize();
            if (maxStack > 1){
                int toGive = qty;
                while (toGive > 0){
                    int stack = Math.min(maxStack, toGive);
                    ItemStack stackItem = new ItemStack(mat, stack);
                    Map<Integer, ItemStack> leftover = p.getInventory().addItem(stackItem);
                    if (!leftover.isEmpty()){
                        allOk = false; // shouldn't happen thanks to pre-check; treat as failure
                        break;
                    }
                    toGive -= stack;
                }
            } else {
                for (int i=0; i<qty; i++){
                    Map<Integer, ItemStack> leftover = p.getInventory().addItem(new ItemStack(mat, 1));
                    if (!leftover.isEmpty()){
                        allOk = false;
                        break;
                    }
                }
            }
        }

        if (!allOk){
            // refund everything (we didn't record purchases yet)
            plugin.economy().depositPlayer(p, totalCost);
            p.sendMessage(ChatColor.RED + "Not enough inventory space. Purchase cancelled and refunded.");
            return false;
        }

        // record purchases + adjust stock
        for (int i=0; i<qty; i++){
            plugin.shopManager().recordPurchase(shopId, p.getUniqueId(), li);
        }

        p.sendMessage(ChatColor.GREEN + "Purchased x" + qty + " for $" + totalCost);

        // Debounced GUI refresh
        var def = plugin.shopManager().getDef(shopId);
        plugin.guiRefresh().refresh(p, def, state.visible);
        return true;
    }

    private boolean dispatch(String exec, Player p, String cmd){
        if ("PLAYER".equals(exec)){
            return p.performCommand(cmd.startsWith("/") ? cmd.substring(1) : cmd);
        } else {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    /** How many of this VANILLA prototype can fit (stack space + empty slots). */
    private int capacityForVanilla(PlayerInventory inv, ItemStack proto){
        if (proto == null || proto.getType() == Material.AIR) return 0;
        int max = proto.getMaxStackSize();
        int capacity = 0;
        // Use storage contents to ignore armor/offhand
        ItemStack[] contents = inv.getStorageContents();
        for (ItemStack s : contents){
            if (s == null || s.getType() == Material.AIR){
                capacity += max;
                continue;
            }
            if (s.isSimilar(proto)){
                capacity += (max - s.getAmount());
            }
        }
        return capacity;
    }

    /** Empty slots in main storage (ignores armor/offhand). */
    private int emptySlots(PlayerInventory inv){
        int empty = 0;
        ItemStack[] contents = inv.getStorageContents();
        for (ItemStack s : contents){
            if (s == null || s.getType() == Material.AIR) empty++;
        }
        return empty;
    }
}
