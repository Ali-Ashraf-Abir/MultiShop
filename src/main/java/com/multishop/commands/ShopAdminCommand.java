package com.multishop.commands;

import com.multishop.MultiShopPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;

import java.util.UUID;

public class ShopAdminCommand implements CommandExecutor {
    private final MultiShopPlugin plugin;
    public ShopAdminCommand(MultiShopPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("multishop.admin")){
            s.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (a.length == 0){
            s.sendMessage(ChatColor.YELLOW +
                    "/shopadmin ban <shop> <player>\n" +
                    "/shopadmin unban <shop> <player>\n" +
                    "/shopadmin pause <shop|all>\n" +
                    "/shopadmin resume <shop|all>\n" +
                    "/shopadmin restock <shop> <player> <itemId> <amount>\n" +
                    "/shopadmin setinterval <minutes>\n" +
                    "/shopadmin reload");
            return true;
        }
        switch (a[0].toLowerCase()){
            case "ban" -> {
                if (a.length < 3){ s.sendMessage("/shopadmin ban <shop> <player>"); break; }
                var def = plugin.shopManager().getDef(a[1]);
                if (def == null){ s.sendMessage("Unknown shop"); break; }
                UUID id = resolve(a[2]);
                if (id == null){ s.sendMessage("Unknown player"); break; }
                plugin.shopManager().ban(def.id, id);
                s.sendMessage("Banned "+a[2]+" from "+def.id);
            }
            case "unban" -> {
                if (a.length < 3){ s.sendMessage("/shopadmin unban <shop> <player>"); break; }
                var def = plugin.shopManager().getDef(a[1]);
                if (def == null){ s.sendMessage("Unknown shop"); break; }
                UUID id = resolve(a[2]);
                if (id == null){ s.sendMessage("Unknown player"); break; }
                plugin.shopManager().unban(def.id, id);
                s.sendMessage("Unbanned "+a[2]+" from "+def.id);
            }
            case "pause" -> {
                if (a.length < 2){ s.sendMessage("/shopadmin pause <shop|all>"); break; }
                if (a[1].equalsIgnoreCase("all")){
                    for (var d : plugin.shopManager().allDefs()) plugin.shopManager().pause(d.id, true);
                    s.sendMessage("All shops paused.");
                } else {
                    var def = plugin.shopManager().getDef(a[1]);
                    if (def == null){ s.sendMessage("Unknown shop"); break; }
                    plugin.shopManager().pause(def.id, true);
                    s.sendMessage(def.id+" paused.");
                }
            }
            case "resume" -> {
                if (a.length < 2){ s.sendMessage("/shopadmin resume <shop|all>"); break; }
                if (a[1].equalsIgnoreCase("all")){
                    for (var d : plugin.shopManager().allDefs()) plugin.shopManager().pause(d.id, false);
                    s.sendMessage("All shops resumed.");
                } else {
                    var def = plugin.shopManager().getDef(a[1]);
                    if (def == null){ s.sendMessage("Unknown shop"); break; }
                    plugin.shopManager().pause(def.id, false);
                    s.sendMessage(def.id+" resumed.");
                }
            }
            case "restock" -> {
                if (a.length < 5){ s.sendMessage("/shopadmin restock <shop> <player> <itemId> <amount>"); break; }
                var def = plugin.shopManager().getDef(a[1]);
                if (def == null){ s.sendMessage("Unknown shop"); break; }
                UUID id = resolve(a[2]);
                if (id == null){ s.sendMessage("Unknown player"); break; }
                int amt;
                try { amt = Integer.parseInt(a[4]); } catch (Exception e){ s.sendMessage("amount must be int"); break; }
                plugin.shopManager().restockForPlayer(def.id, id, a[3], amt);
                s.sendMessage("Restocked "+a[2]+" for item "+a[3]+" by "+amt);
            }
            case "setinterval" -> {
                if (a.length < 2){ s.sendMessage("/shopadmin setinterval <minutes>"); break; }
                int m;
                try { m = Integer.parseInt(a[1]); } catch (Exception e){ s.sendMessage("minutes must be int"); break; }
                plugin.shopManager().setIntervalMinutes(m);
                s.sendMessage("Interval set to "+m+" minutes. Realigns to clock.");
            }
            case "reload" -> {
                plugin.reloadConfig();
                s.sendMessage("Config reloaded. (Shops reload requires server restart for now.)");
            }
            default -> {}
        }
        return true;
    }

    private UUID resolve(String name){
        OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
        if (op != null) return op.getUniqueId();
        var online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        return null;
    }
}
