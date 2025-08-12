package com.multishop.commands;

import com.multishop.MultiShopPlugin;
import com.multishop.config.ShopDefinition;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;
import java.util.stream.Collectors;

public class ShopAdminTabCompleter implements TabCompleter {

    private final MultiShopPlugin plugin;

    public ShopAdminTabCompleter(MultiShopPlugin plugin){
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("multishop.admin")) return List.of();

        // Subcommands we support
        List<String> subs = List.of(
                "ban", "unban",
                "pause", "resume",
                "stopall", "startall",
                "setinterval",
                "restock" // restock <shopId> <player> <itemId> <amount>
        );

        if (args.length == 1){
            return filter(subs, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub){
            case "ban":
            case "unban":
                // /shopadmin ban <shopId> <player>
                if (args.length == 2) return filter(shopIds(), args[1]);
                if (args.length == 3) return filter(playerNames(), args[2]);
                return List.of();

            case "pause":
            case "resume":
                // /shopadmin pause <shopId>
                if (args.length == 2) return filter(shopIds(), args[1]);
                return List.of();

            case "setinterval":
                // /shopadmin setinterval <minutes>
                if (args.length == 2) return filter(List.of("1","2","3","5","10","15","30","60"), args[1]);
                return List.of();

            case "stopall":
            case "startall":
                // no more args
                return List.of();

            case "restock":
                // /shopadmin restock <shopId> <player> <itemId> <amount>
                if (args.length == 2) return filter(shopIds(), args[1]);
                if (args.length == 3) return filter(playerNames(), args[2]);
                if (args.length == 4) {
                    // suggest item ids for that shop
                    String shopId = args[1];
                    var def = plugin.shopManager().getDef(shopId);
                    if (def != null) {
                        List<String> itemIds = def.items.stream().map(it -> it.id).filter(Objects::nonNull).toList();
                        return filter(itemIds, args[3]);
                    }
                    return List.of();
                }
                if (args.length == 5) return filter(List.of("1","2","3","5","10"), args[4]);
                return List.of();
        }

        return List.of();
    }

    private List<String> shopIds(){
        List<String> ids = new ArrayList<>();
        for (ShopDefinition d : plugin.shopManager().allDefs()){
            ids.add(d.id);
        }
        return ids;
    }

    private List<String> playerNames(){
        // prefer online names; fall back to recent offline if needed
        Set<String> names = new HashSet<>();
        Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
        if (names.isEmpty()){
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()){
                if (op.getName() != null) names.add(op.getName());
                if (names.size() > 20) break; // don't flood
            }
        }
        return names.stream().sorted().collect(Collectors.toList());
    }

    private static List<String> filter(List<String> items, String prefix){
        if (prefix == null || prefix.isEmpty()) return items.stream().distinct().sorted().collect(Collectors.toList());
        String p = prefix.toLowerCase(Locale.ROOT);
        return items.stream()
                .filter(Objects::nonNull)
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
