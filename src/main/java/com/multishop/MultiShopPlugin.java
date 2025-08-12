package com.multishop;

import com.multishop.commands.ShopTimerCommand;
import com.multishop.db.*;
import com.multishop.service.*;
import com.multishop.config.ShopLoader;
import com.multishop.commands.ShopAdminCommand;
import com.multishop.commands.ShopCommand;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class MultiShopPlugin extends JavaPlugin {

    private static MultiShopPlugin instance;
    private Economy economy;
    private Storage storage;
    private ShopManager shopManager;
    private TimerBarService timerBar;
    private ActionBarService actionBar;

    public static MultiShopPlugin inst(){ return instance; }
    public Economy economy(){ return economy; }
    public Storage storage(){ return storage; }
    public ShopManager shopManager(){ return shopManager; }
    public TimerBarService timerBar(){ return timerBar; }
    public ActionBarService actionBar(){ return actionBar; }
    private com.multishop.service.QuantityPromptService quantityPrompt;
    public com.multishop.service.QuantityPromptService quantityPrompt(){ return quantityPrompt; }
    private com.multishop.service.BanService banService;
    public com.multishop.service.BanService banService(){ return banService; }
    private com.multishop.service.DbQueueService dbQueue;
    private com.multishop.service.GuiRefreshService guiRefresh;
    public com.multishop.service.DbQueueService dbQueue(){ return dbQueue; }
    public com.multishop.service.GuiRefreshService guiRefresh(){ return guiRefresh; }
    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        banService = new com.multishop.service.BanService(this);
        if (!setupEconomy()) {
            getLogger().severe("Vault economy not found. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        String type = getConfig().getString("database.type", "sqlite");
        switch (type.toLowerCase()) {
            case "mysql" -> storage = new MysqlStorage(this);
            default -> storage = new SqliteStorage(this);
        }
        storage.init();
        dbQueue = new com.multishop.service.DbQueueService(this);
        guiRefresh = new com.multishop.service.GuiRefreshService(this);
        ShopLoader loader = new ShopLoader(this);
        var definitions = loader.loadAll();

        timerBar = new TimerBarService(this);
        actionBar = new ActionBarService(this);
        shopManager = new ShopManager(this, definitions);
        shopManager.initializeSchedule();
        shopManager.syncBansFromStore();

        getCommand("shop").setExecutor(new ShopCommand(this));
        getCommand("shopadmin").setExecutor(new ShopAdminCommand(this));
        getCommand("shop").setExecutor(new ShopCommand(this));
        getCommand("shopadmin").setExecutor(new ShopAdminCommand(this));
        getCommand("shoptimer").setExecutor(new ShopTimerCommand(this));
        quantityPrompt = new com.multishop.service.QuantityPromptService(this);
        getCommand("shop").setTabCompleter(new com.multishop.commands.ShopTabCompleter(this));
        getCommand("shopadmin").setTabCompleter(new com.multishop.commands.ShopAdminTabCompleter(this));
        getCommand("shoptimer").setTabCompleter(new com.multishop.commands.ShopTimerTabCompleter());
        // Register GUI listeners
        new com.multishop.gui.GuiListener(this);

        getLogger().info("MultiShop enabled.");
    }

    @Override
    public void onDisable() {
        if (dbQueue != null) dbQueue.shutdown();
        if (shopManager != null) shopManager.shutdown();
        if (storage != null) storage.shutdown();
        getLogger().info("MultiShop disabled.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }
}
