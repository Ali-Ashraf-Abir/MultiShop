package com.multishop.service;

import com.multishop.MultiShopPlugin;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TimerBarService {
    private final MultiShopPlugin plugin;
    private final BossBar bar;
    private final Set<UUID> optedIn = new HashSet<>();
    private Instant nextReset = Instant.now();

    public TimerBarService(MultiShopPlugin plugin){
        this.plugin = plugin;
        bar = Bukkit.createBossBar("Shop reset soon", BarColor.BLUE, BarStyle.SEGMENTED_10);
        bar.setVisible(false);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (bar.getPlayers().isEmpty()) return;
            long total = Math.max(1, Duration.between(Instant.now(), nextReset).toSeconds());
            double frac = 1.0 - (total / (double)Math.max(1, plugin.shopManager().secondsPerInterval()));
            bar.setProgress(Math.max(0, Math.min(1.0, frac)));
            bar.setTitle("Shop resets in " + total + "s");
        }, 20L, 20L);
    }

    public void setNextReset(Instant at){ this.nextReset = at; }
    public Instant getNextReset(){ return nextReset; }

    public boolean isOptedIn(UUID id){ return optedIn.contains(id); }
    public void setOpt(UUID id, boolean enable){
        if (enable) optedIn.add(id); else {
            optedIn.remove(id);
            hide(id);
        }
    }
    public void toggle(UUID id){ setOpt(id, !isOptedIn(id)); }

    public void show(UUID uuid){
        if (!optedIn.contains(uuid)) return;
        var p = Bukkit.getPlayer(uuid);
        if (p == null) return;
        if (!bar.getPlayers().contains(p)){
            bar.addPlayer(p);
            bar.setVisible(true);
        }
    }
    public void hide(UUID uuid){
        var p = Bukkit.getPlayer(uuid);
        if (p == null) return;
        bar.removePlayer(p);
        if (bar.getPlayers().isEmpty()) bar.setVisible(false);
    }
}
