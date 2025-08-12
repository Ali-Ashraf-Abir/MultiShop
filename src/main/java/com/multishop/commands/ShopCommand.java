package com.multishop.commands;

import com.multishop.MultiShopPlugin;
import com.multishop.gui.ShopGUI;
import com.multishop.config.ShopDefinition;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class ShopCommand implements CommandExecutor {
    private final MultiShopPlugin plugin;
    public ShopCommand(MultiShopPlugin plugin){ this.plugin = plugin; }

    private ShopDefinition resolve(String arg){
        if (arg == null) return null;
        String key = arg.toLowerCase();
        // prefer command match
        for (var d : plugin.shopManager().allDefs()){
            if (d.command != null && d.command.equalsIgnoreCase(arg)) return d;
        }
        // fallback: id match
        var byId = plugin.shopManager().getDef(arg);
        return byId;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)){
            sender.sendMessage("Players only.");
            return true;
        }
        if (!p.hasPermission("multishop.use")){
            p.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length < 1){
            p.sendMessage(ChatColor.YELLOW + "Use: /shop <shop-command>");
            return true;
        }
        var def = resolve(args[0]);
        if (def == null){
            p.sendMessage(ChatColor.RED + "Unknown shop.");
            return true;
        }
        var state = plugin.shopManager().liveState(def.id);
        if (state.paused){
            p.sendMessage(ChatColor.RED + "This shop is paused.");
            return true;
        }
        if (plugin.shopManager().isBanned(def.id, p.getUniqueId())){
            p.sendMessage(ChatColor.RED + "You are banned from this shop.");
            return true;
        }

        new ShopGUI(plugin).open(p, def, state.visible);
        return true;
    }
}
