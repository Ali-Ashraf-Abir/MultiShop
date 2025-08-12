package com.multishop.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class ShopTimerTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1){
            return filter(List.of("on","off","toggle"), args[0]);
        }
        return List.of();
    }
    private static List<String> filter(List<String> items, String prefix){
        if (prefix == null || prefix.isEmpty()) return items;
        String p = prefix.toLowerCase(Locale.ROOT);
        return items.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(p)).collect(Collectors.toList());
    }
}
