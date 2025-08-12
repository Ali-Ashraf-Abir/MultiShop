package com.multishop.service;

import com.multishop.MultiShopPlugin;
import com.multishop.config.ShopDefinition;
import com.multishop.gui.ShopGUI;
import com.multishop.model.ShopItemLive;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GuiRefreshService {
    private final MultiShopPlugin plugin;
    private final String mode; // debounced | reopen
    private final int debounceMs;
    private final Map<UUID, Integer> pending = new ConcurrentHashMap<>();

    public GuiRefreshService(MultiShopPlugin plugin){
        this.plugin = plugin;
        var sec = plugin.getConfig().getConfigurationSection("performance.gui");
        this.mode = (sec != null ? sec.getString("refresh-mode", "debounced") : "debounced").toLowerCase(Locale.ROOT);
        this.debounceMs = sec != null ? sec.getInt("refresh-debounce-ms", 150) : 150;
    }

    public void refresh(Player p, ShopDefinition def, List<ShopItemLive> items){
        if (!"debounced".equals(mode)){
            new ShopGUI(plugin).open(p, def, items);
            return;
        }
        // coalesce multiple buys into one reopen
        Integer old = pending.remove(p.getUniqueId());
        if (old != null) Bukkit.getScheduler().cancelTask(old);

        int ticks = Math.max(1, debounceMs / 50);
        int tid = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            pending.remove(p.getUniqueId());
            if (p.isOnline()) new ShopGUI(plugin).open(p, def, items);
        }, ticks);
        pending.put(p.getUniqueId(), tid);
    }
}
