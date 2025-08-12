package com.multishop.db;

import com.multishop.MultiShopPlugin;
import com.multishop.model.ShopItemLive;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.time.Instant;
import java.util.List;

public class MysqlStorage implements Storage {
    private final MultiShopPlugin plugin;
    private HikariDataSource ds;
    private boolean ready = false;

    public MysqlStorage(MultiShopPlugin plugin){ this.plugin = plugin; }

    @Override public boolean isReady(){ return ready; }

    @Override
    public void init() {
        try {
            var c = plugin.getConfig().getConfigurationSection("database.mysql");
            if (c == null) throw new IllegalStateException("Missing database.mysql section in config.yml");

            HikariConfig hc = new HikariConfig();
            String jdbc = String.format(
                    "jdbc:mysql://%s:%d/%s?useSSL=%s&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    c.getString("host"), c.getInt("port"), c.getString("database"), c.getBoolean("useSSL"));
            hc.setJdbcUrl(jdbc);
            hc.setUsername(c.getString("user"));
            hc.setPassword(c.getString("password"));
            hc.setMaximumPoolSize(Math.max(4, c.getInt("poolMax", 10)));
            hc.setMinimumIdle(Math.max(1, c.getInt("poolMin", 2)));
            hc.setPoolName("MultiShopPool");
            hc.addDataSourceProperty("cachePrepStmts", "true");
            hc.addDataSourceProperty("prepStmtCacheSize", "250");
            hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            ds = new HikariDataSource(hc);

            try (Connection cx = ds.getConnection()) {
                createTables(cx);
            }
            ready = true;
            plugin.getLogger().info("[MultiShop] MySQL connected and schema ready.");
        } catch (Exception e) {
            ready = false;
            plugin.getLogger().severe("[MultiShop] MySQL init FAILED: " + e.getMessage());
        }
    }

    private void createTables(Connection cx) throws SQLException {
        try (Statement st = cx.createStatement()){
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS current_shop_state(
                  shop_id VARCHAR(64),
                  item_id VARCHAR(64),
                  display_name VARCHAR(128),
                  type VARCHAR(16),
                  material VARCHAR(64),
                  icon VARCHAR(64),
                  price INT,
                  max_per_player INT,
                  stock_remaining INT,
                  shuffled_at BIGINT,
                  cycle_ends_at BIGINT,
                  is_available TINYINT,
                  unlimited_stock TINYINT,
                  INDEX(shop_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
            """);
        }
    }

    @Override
    public void writeSnapshot(String shopId, List<ShopItemLive> items, Instant at, Instant endsAt) {
        if (!ready || ds == null) {
            plugin.getLogger().warning("[MultiShop] DB not ready; skipping snapshot for " + shopId);
            return;
        }
        wipeShop(shopId);
        String sql = "INSERT INTO current_shop_state(shop_id,item_id,display_name,type,material,icon,price,max_per_player,stock_remaining,shuffled_at,cycle_ends_at,is_available,unlimited_stock) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection cx = ds.getConnection();
             PreparedStatement ps = cx.prepareStatement(sql)) {
            for (ShopItemLive it : items){
                ps.setString(1, shopId);
                ps.setString(2, it.def.id);
                ps.setString(3, it.def.displayName != null ? it.def.displayName : it.def.id);
                ps.setString(4, it.def.type);
                ps.setString(5, it.def.material);
                ps.setString(6, it.def.icon);
                ps.setInt(7, it.def.price);
                ps.setInt(8, it.def.maxPerPlayer);
                ps.setInt(9, it.def.unlimitedStock ? -1 : it.stockRemaining); // -1 denotes unlimited
                ps.setLong(10, at.getEpochSecond());
                ps.setLong(11, endsAt.getEpochSecond());
                ps.setInt(12, it.available ? 1 : 0);
                ps.setInt(13, it.def.unlimitedStock ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL snapshot insert failed: " + e.getMessage());
        }
    }

    @Override
    public void wipeShop(String shopId) {
        if (!ready || ds == null) return;
        try (Connection cx = ds.getConnection();
             PreparedStatement del = cx.prepareStatement("DELETE FROM current_shop_state WHERE shop_id=?")) {
            del.setString(1, shopId);
            del.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("MySQL wipe failed: " + e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        try { if (ds != null) ds.close(); } catch (Exception ignored){}
    }
}
