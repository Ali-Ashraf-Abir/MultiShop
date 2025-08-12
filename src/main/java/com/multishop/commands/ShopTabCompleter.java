package com.multishop.commands;

import com.multishop.MultiShopPlugin;
import com.multishop.config.ShopDefinition;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ShopTabCompleter implements TabCompleter {

    private final MultiShopPlugin plugin;

    public ShopTabCompleter(MultiShopPlugin plugin){
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        // /shop <shop-command or id>
        if (args.length == 1){
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> opts = new ArrayList<>();

            for (ShopDefinition d : plugin.shopManager().allDefs()){
                if (d.command != null && !d.command.isEmpty()) opts.add(d.command);
                if (d.id != null && !d.id.isEmpty()) opts.add(d.id);
            }
            return filter(opts, prefix);
        }
        return List.of();
    }

    private static List<String> filter(List<String> items, String prefix){
        if (prefix == null || prefix.isEmpty()) return items.stream().distinct().sorted().collect(Collectors.toList());
        String p = prefix.toLowerCase(Locale.ROOT);
        return items.stream()
                .filter(s -> s != null && s.toLowerCase(Locale.ROOT).startsWith(p))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
