package com.multishop.service;

import com.multishop.MultiShopPlugin;
import com.multishop.config.ShopDefinition;
import com.multishop.config.ShopItemDef;
import com.multishop.model.ActiveShopState;
import com.multishop.model.ShopItemLive;
import com.multishop.util.ItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ShopManager {
    private final MultiShopPlugin plugin;
    private final Map<String, ShopDefinition> defs = new HashMap<>();
    private final Map<String, ActiveShopState> live = new HashMap<>();
    private int taskId = -1;
    private int intervalMinutes;

    public ShopManager(MultiShopPlugin plugin, List<ShopDefinition> definitions){
        this.plugin = plugin;
        for (var d : definitions){
            defs.put(d.id.toLowerCase(), d);
            live.put(d.id.toLowerCase(), new ActiveShopState(d.id.toLowerCase()));
        }
        intervalMinutes = Math.max(1, plugin.getConfig().getInt("interval-minutes", 5));
    }

    public int secondsPerInterval(){ return intervalMinutes * 60; }
    public Collection<ShopDefinition> allDefs(){ return defs.values(); }
    public ActiveShopState liveState(String shopId){ return live.get(shopId.toLowerCase()); }
    public ShopDefinition getDef(String id){ return defs.get(id.toLowerCase()); }

    /** Load persisted bans into live state. Call from onEnable() after creating this manager. */
    public void syncBansFromStore(){
        for (var d : defs.values()){
            var s = live.get(d.id.toLowerCase());
            if (s == null) continue;
            s.bannedPlayers.clear();
            s.bannedPlayers.addAll(plugin.banService().allBanned(d.id));
        }
    }

    public void initializeSchedule(){
        Instant now = Instant.now();
        ZonedDateTime z = ZonedDateTime.ofInstant(now, ZoneId.systemDefault());
        int minute = z.getMinute();
        int mod = minute % intervalMinutes;
        int add = (mod == 0 && z.getSecond()==0) ? 0 : (intervalMinutes - mod);
        ZonedDateTime next = z.withSecond(0).withNano(0).plusMinutes(add);

        plugin.timerBar().setNextReset(next.toInstant());
        plugin.actionBar().setNextReset(next.toInstant());

        long delayTicks = Math.max(1, Duration.between(now, next.toInstant()).toSeconds()) * 20L;
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::reshuffleAll, delayTicks, intervalMinutes * 60L * 20L);
        plugin.getLogger().info("First shuffle at "+next+" then every "+intervalMinutes+" min.");
    }

    public void shutdown(){
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
    }

    private void reshuffleAll(){
        Instant at = Instant.now();
        Instant endsAt = at.plusSeconds(intervalMinutes * 60L);
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (var def : defs.values()){
            var state = live.get(def.id.toLowerCase());
            if (state == null || state.paused) continue;

            List<ShopItemLive> all = new ArrayList<>();
            state.indexById.clear();

            for (ShopItemDef sd : def.items){
                // icon
                Material m;
                if ("VANILLA".equalsIgnoreCase(sd.type)) {
                    m = Material.matchMaterial(sd.material == null ? "STONE" : sd.material);
                } else {
                    m = Material.matchMaterial(sd.icon == null ? "PAPER" : sd.icon);
                }
                if (m == null) m = Material.STONE;
                ItemStack icon = ItemUtil.withMeta(new ItemStack(m), sd.displayName, sd.lore);

                ShopItemLive li = new ShopItemLive(sd, icon);

                // chance roll
                int roll = rng.nextInt(1, 101);
                li.available = roll <= Math.max(0, Math.min(100, sd.chance));

                // stock
                if (li.available) {
                    if (sd.unlimitedStock) {
                        li.stockRemaining = Integer.MAX_VALUE;
                    } else {
                        int minS = sd.minStock != null ? Math.max(0, sd.minStock) : 0;
                        int maxS = sd.maxStock != null ? Math.max(minS, sd.maxStock) : 0;
                        li.stockRemaining = rng.nextInt(minS, maxS + 1);
                    }
                } else {
                    li.stockRemaining = 0;
                }

                state.indexById.put(sd.id, all.size());
                all.add(li);
            }

            state.visible = all;                // show all items, GUI marks availability
            state.perPlayerBought.clear();
            state.shuffledAt = at;

            // write snapshot immediately via async writer
            try {
                plugin.dbQueue().writeNow(def.id, all, at, endsAt);
            } catch (Exception ex) {
                plugin.getLogger().warning("[MultiShop] Snapshot write failed for "+def.id+": " + ex.getMessage());
            }
        }

        plugin.timerBar().setNextReset(endsAt);
        plugin.actionBar().setNextReset(endsAt);

        for (Player p : Bukkit.getOnlinePlayers()){
            plugin.timerBar().show(p.getUniqueId()); // only if opted-in
        }
    }

    // Persistent ban helpers (delegate to BanService)
    public boolean isBanned(String shopId, UUID uuid){ return plugin.banService().isBanned(shopId, uuid); }
    public void ban(String shopId, UUID uuid){
        plugin.banService().ban(shopId, uuid);
        var s = live.get(shopId.toLowerCase());
        if (s != null) s.bannedPlayers.add(uuid);
    }
    public void unban(String shopId, UUID uuid){
        plugin.banService().unban(shopId, uuid);
        var s = live.get(shopId.toLowerCase());
        if (s != null) s.bannedPlayers.remove(uuid);
    }

    public void pause(String shopId, boolean value){ live.get(shopId.toLowerCase()).paused = value; }

    public void setIntervalMinutes(int minutes){
        this.intervalMinutes = Math.max(1, minutes);
        if (plugin.isEnabled()){
            shutdown();
            initializeSchedule();
        }
    }

    public void restockForPlayer(String shopId, UUID player, String itemId, int amount){
        var s = live.get(shopId.toLowerCase());
        if (s == null) return;
        var map = s.perPlayerBought.computeIfAbsent(player, k->new HashMap<>());
        map.put(itemId, Math.max(0, map.getOrDefault(itemId,0) - amount));
    }

    public boolean canPurchase(String shopId, UUID uuid, ShopItemLive li){
        var s = live.get(shopId.toLowerCase());
        if (s == null) return false;
        if (!li.available) return false;
        if (!li.def.unlimitedStock && li.stockRemaining <= 0) return false;

        var map = s.perPlayerBought.computeIfAbsent(uuid, k->new HashMap<>());
        int have = map.getOrDefault(li.def.id, 0);
        return have < li.def.maxPerPlayer;
    }

    public void recordPurchase(String shopId, UUID uuid, ShopItemLive li){
        var s = live.get(shopId.toLowerCase());
        if (s == null) return;
        var map = s.perPlayerBought.computeIfAbsent(uuid, k->new HashMap<>());
        map.put(li.def.id, map.getOrDefault(li.def.id, 0) + 1);

        if (!li.def.unlimitedStock) {
            li.stockRemaining = Math.max(0, li.stockRemaining - 1);
        }

        // enqueue a debounced snapshot if realtime=true
        if (plugin.storage().isReady()){
            try {
                plugin.dbQueue().maybeQueue(shopId, s.visible, s.shuffledAt, plugin.timerBar().getNextReset());
            } catch (Exception ignored){}
        }
    }
}
