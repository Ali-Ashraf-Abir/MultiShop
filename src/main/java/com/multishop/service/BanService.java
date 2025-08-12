package com.multishop.service;

import com.multishop.MultiShopPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class BanService {

    private final MultiShopPlugin plugin;
    private final File file;
    private FileConfiguration data;
    private final Map<String, Set<UUID>> bans = new HashMap<>();

    public BanService(MultiShopPlugin plugin){
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bans.yml");
        load();
    }

    public void load(){
        if (!file.exists()){
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("[MultiShop] Failed creating bans.yml: " + e.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
        bans.clear();
        if (data.getConfigurationSection("bans") != null){
            for (String shopId : data.getConfigurationSection("bans").getKeys(false)){
                List<String> list = data.getStringList("bans." + shopId);
                Set<UUID> set = new HashSet<>();
                for (String s : list){
                    try { set.add(UUID.fromString(s)); } catch (Exception ignored){}
                }
                bans.put(shopId.toLowerCase(), set);
            }
        }
        plugin.getLogger().info("[MultiShop] Loaded bans for " + bans.size() + " shops.");
    }

    public void save(){
        YamlConfiguration out = new YamlConfiguration();
        Map<String, List<String>> toWrite = new HashMap<>();
        for (var e : bans.entrySet()){
            List<String> lst = new ArrayList<>();
            for (UUID u : e.getValue()) lst.add(u.toString());
            toWrite.put(e.getKey(), lst);
        }
        out.createSection("bans", toWrite);
        try {
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[MultiShop] Failed saving bans.yml: " + e.getMessage());
        }
    }

    public boolean isBanned(String shopId, UUID uuid){
        return bans.getOrDefault(shopId.toLowerCase(), Collections.emptySet()).contains(uuid);
    }

    public void ban(String shopId, UUID uuid){
        bans.computeIfAbsent(shopId.toLowerCase(), k->new HashSet<>()).add(uuid);
        save();
    }

    public void unban(String shopId, UUID uuid){
        var set = bans.get(shopId.toLowerCase());
        if (set != null){
            set.remove(uuid);
            save();
        }
    }

    public Set<UUID> allBanned(String shopId){
        return new HashSet<>(bans.getOrDefault(shopId.toLowerCase(), Collections.emptySet()));
    }
}
