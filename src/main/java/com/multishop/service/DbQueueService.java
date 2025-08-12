package com.multishop.service;

import com.multishop.MultiShopPlugin;
import com.multishop.model.ShopItemLive;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DbQueueService {
    private final MultiShopPlugin plugin;

    private final boolean realtime;
    private final int debounceMs;

    private final Map<String, Snapshot> pending = new ConcurrentHashMap<>();
    private int taskId = -1;

    public DbQueueService(MultiShopPlugin plugin){
        this.plugin = plugin;
        var sec = plugin.getConfig().getConfigurationSection("performance.storage");
        this.realtime = sec != null && sec.getBoolean("realtime", false);
        this.debounceMs = sec != null ? sec.getInt("debounced-ms", 300) : 300;

        if (realtime){
            long periodTicks = Math.max(1, debounceMs / 50);
            taskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, this::flush, periodTicks, periodTicks);
        }
    }

    public void shutdown(){
        if (taskId != -1) plugin.getServer().getScheduler().cancelTask(taskId);
        flush();
    }

    /** Always write now (used by reshuffle). */
    public void writeNow(String shopId, List<ShopItemLive> items, Instant at, Instant endsAt){
        try {
            plugin.storage().writeSnapshot(shopId, items, at, endsAt);
        } catch (Exception ex){
            plugin.getLogger().warning("[MultiShop] writeNow failed for "+shopId+": " + ex.getMessage());
        }
    }

    /** On purchase: enqueue if realtime on; no-op if realtime off. */
    public void maybeQueue(String shopId, List<ShopItemLive> items, Instant at, Instant endsAt){
        if (!realtime) return;
        pending.put(shopId, new Snapshot(shopId, items, at, endsAt));
    }

    private void flush(){
        if (pending.isEmpty()) return;
        var copy = new ArrayList<>(pending.values());
        pending.clear();
        for (Snapshot s : copy){
            writeNow(s.shopId, s.items, s.at, s.endsAt);
        }
    }

    private record Snapshot(String shopId, List<ShopItemLive> items, Instant at, Instant endsAt) {}
}
