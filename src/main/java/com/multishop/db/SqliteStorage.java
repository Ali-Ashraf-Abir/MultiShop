package com.multishop.db;

import com.multishop.model.ShopItemLive;
import com.multishop.MultiShopPlugin;

import java.sql.*;
import java.time.Instant;
import java.util.List;

public class SqliteStorage implements Storage {
    private final MultiShopPlugin plugin;
    private Connection conn;
    private boolean ready = false;

    public SqliteStorage(MultiShopPlugin plugin){ this.plugin = plugin; }

    @Override public boolean isReady(){ return ready; }

    @Override public void init() {
        try {
            String path = plugin.getConfig().getString("database.sqlite.file");
            conn = DriverManager.getConnection("jdbc:sqlite:" + path);
            createTables();
            ready = true;
            plugin.getLogger().info("[MultiShop] SQLite connected and schema ready.");
        } catch (Exception e) {
            ready = false;
            plugin.getLogger().severe("SQLite init failed: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement st = conn.createStatement()){
            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS current_shop_state(
                  shop_id TEXT,
                  item_id TEXT,
                  display_name TEXT,
                  type TEXT,
                  material TEXT,
                  icon TEXT,
                  price INT,
                  max_per_player INT,
                  stock_remaining INT,
                  shuffled_at BIGINT,
                  cycle_ends_at BIGINT,
                  is_available TINYINT,
                  unlimited_stock TINYINT
                )
            """);
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_shop ON current_shop_state(shop_id)");
        }
    }


    @Override public void writeSnapshot(String shopId, List<ShopItemLive> items, Instant at, Instant endsAt) {
        if (!ready || conn == null){
            plugin.getLogger().warning("[MultiShop] SQLite not ready; skip snapshot.");
            return;
        }
        wipeShop(shopId);
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO current_shop_state(shop_id,item_id,display_name,type,material,icon,price,max_per_player,stock_remaining,shuffled_at,cycle_ends_at,is_available,unlimited_stock) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            for (ShopItemLive it : items){
                ps.setString(1, shopId);
                ps.setString(2, it.def.id);
                ps.setString(3, it.def.displayName != null ? it.def.displayName : it.def.id);
                ps.setString(4, it.def.type);
                ps.setString(5, it.def.material);
                ps.setString(6, it.def.icon);
                ps.setInt(7, it.def.price);
                ps.setInt(8, it.def.maxPerPlayer);
                ps.setInt(9, it.def.unlimitedStock ? -1 : it.stockRemaining); // -1 = unlimited
                ps.setLong(10, at.getEpochSecond());
                ps.setLong(11, endsAt.getEpochSecond());
                ps.setInt(12, it.available ? 1 : 0);
                ps.setInt(13, it.def.unlimitedStock ? 1 : 0);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            plugin.getLogger().warning("SQLite snapshot insert failed: " + e.getMessage());
        }
    }

    @Override public void wipeShop(String shopId) {
        if (!ready || conn == null) return;
        try (PreparedStatement del = conn.prepareStatement("DELETE FROM current_shop_state WHERE shop_id=?")) {
            del.setString(1, shopId);
            del.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("SQLite wipe failed: " + e.getMessage());
        }
    }

    @Override public void shutdown() {
        try { if (conn != null) conn.close(); } catch (Exception ignored){}
    }
}
