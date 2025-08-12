package com.multishop.commands;

import com.multishop.MultiShopPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class ShopTimerCommand implements CommandExecutor {
    private final MultiShopPlugin plugin;
    public ShopTimerCommand(MultiShopPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)){ sender.sendMessage("Players only."); return true; }

        if (args.length == 0 || args[0].equalsIgnoreCase("toggle")){
            plugin.timerBar().toggle(p.getUniqueId());
            boolean on = plugin.timerBar().isOptedIn(p.getUniqueId());
            p.sendMessage(ChatColor.YELLOW + "Shop timer " + (on ? "enabled" : "disabled") + ".");
            if (on) plugin.timerBar().show(p.getUniqueId()); else plugin.timerBar().hide(p.getUniqueId());
            return true;
        }

        if (args[0].equalsIgnoreCase("on")){
            plugin.timerBar().setOpt(p.getUniqueId(), true);
            plugin.timerBar().show(p.getUniqueId());
            p.sendMessage(ChatColor.YELLOW + "Shop timer enabled.");
            return true;
        }

        if (args[0].equalsIgnoreCase("off")){
            plugin.timerBar().setOpt(p.getUniqueId(), false);
            p.sendMessage(ChatColor.YELLOW + "Shop timer disabled.");
            return true;
        }

        p.sendMessage(ChatColor.YELLOW + "Usage: /shoptimer [on|off|toggle]");
        return true;
    }
}
