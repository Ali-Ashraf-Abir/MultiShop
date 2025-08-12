package com.multishop.db;

import com.multishop.model.ShopItemLive;

import java.time.Instant;
import java.util.List;

public interface Storage {
    void init();
    boolean isReady();
    void writeSnapshot(String shopId, List<ShopItemLive> items, Instant at, Instant endsAt);
    void wipeShop(String shopId);
    void shutdown();
}
