package com.multishop.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public class ShopLoader {
    private final JavaPlugin plugin;
    public ShopLoader(JavaPlugin plugin){ this.plugin = plugin; }

    public List<ShopDefinition> loadAll(){
        File dir = new File(plugin.getDataFolder(), "shops");
        if (!dir.exists()) dir.mkdirs();

        List<ShopDefinition> out = new ArrayList<>();
        File[] files = dir.listFiles((d,f)->f.endsWith(".yml"));
        if (files == null) return out;

        for (File f : files){
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);

            ShopDefinition def = new ShopDefinition();
            def.id = y.getString("id", f.getName().replace(".yml",""));
            def.displayName = y.getString("display-name", def.id);
            def.command = y.getString("command", def.id);
            def.maxPoolSize = y.isInt("max-pool-size") ? y.getInt("max-pool-size") : null;
            def.visibleSlots = y.getInt("visible-slots", 9);

            List<ShopItemDef> list = new ArrayList<>();
            List<Map<?, ?>> rawItems = y.getMapList("items");
            for (Map<?, ?> m : rawItems){
                ShopItemDef it = new ShopItemDef();
                it.id            = str(m, "id");
                it.type          = str(m, "type");
                it.material      = str(m, "material");
                it.icon          = str(m, "icon");
                it.displayName   = str(m, "display-name");
                it.lore          = strList(m, "lore");

                it.price         = clamp(intVal(m, "price", 0), 0, Integer.MAX_VALUE);
                it.chance        = clamp(intVal(m, "chance", 0), 0, 100);
                it.maxPerPlayer  = Math.max(1, clamp(intVal(m, "max-per-player", 1), 1, Integer.MAX_VALUE));

                it.minStock      = m.containsKey("min-stock") ? Math.max(0, intVal(m, "min-stock", 0)) : null;
                it.maxStock      = m.containsKey("max-stock") ? Math.max(0, intVal(m, "max-stock", 0)) : null;

                // NEW: unlimited-stock
                Object u = m.get("unlimited-stock");
                it.unlimitedStock = (u instanceof Boolean b && b) ||
                        (u instanceof String s && s.equalsIgnoreCase("true"));

                it.giveCommand   = str(m, "give-command");
                it.executeAs     = str(m, "execute-as");

                // Sanity: if both min/max given and min>max, swap
                if (it.minStock != null && it.maxStock != null && it.minStock > it.maxStock){
                    int tmp = it.minStock; it.minStock = it.maxStock; it.maxStock = tmp;
                }
                list.add(it);
            }
            def.items = list;

            plugin.getLogger().info("[MultiShop] Loaded shop " + def.id + " with " + def.items.size() + " items.");
            out.add(def);
        }
        return out;
    }

    private static String str(Map<?,?> m, String k){
        Object o = m.get(k);
        return (o == null) ? null : String.valueOf(o);
    }
    private static int intVal(Map<?,?> m, String k, int def){
        Object o = m.get(k);
        if (o instanceof Number n) return n.intValue();
        if (o instanceof String s){
            try { return Integer.parseInt(s.trim()); } catch (Exception ignored) {}
        }
        return def;
    }
    private static List<String> strList(Map<?,?> m, String k){
        Object o = m.get(k);
        if (o instanceof List<?> lst){
            List<String> out = new ArrayList<>();
            for (Object e : lst) out.add(String.valueOf(e));
            return out;
        }
        return new ArrayList<>();
    }
    private static int clamp(int v, int lo, int hi){ return Math.max(lo, Math.min(hi, v)); }
}
