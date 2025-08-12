package com.multishop.service;

import com.multishop.MultiShopPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class ActionBarService {
    private final MultiShopPlugin plugin;
    private final Set<UUID> watching = new HashSet<>();
    private Instant nextReset = Instant.now();

    public ActionBarService(MultiShopPlugin plugin){
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (watching.isEmpty()) return;
            long secs = Math.max(1, Duration.between(Instant.now(), nextReset).toSeconds());
            Title title = Title.title(
                    Component.empty(),
                    Component.text("Shop resets in " + secs + "s")
            );
            for (UUID id : watching){
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) p.showTitle(title);
            }
        }, 20L, 20L);
    }

    public void setNextReset(Instant at){ this.nextReset = at; }
    public void watch(UUID id){ watching.add(id); }
    public void unwatch(UUID id){ watching.remove(id); }
}
