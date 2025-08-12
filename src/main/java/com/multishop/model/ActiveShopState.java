package com.multishop.model;

import java.time.Instant;
import java.util.*;

public class ActiveShopState {
    public final String shopId;
    public List<ShopItemLive> visible;
    public Map<UUID, Map<String,Integer>> perPlayerBought;
    public boolean paused = false;
    public Set<UUID> bannedPlayers = new HashSet<>();
    public Instant shuffledAt;

    // quick lookup: itemId -> index in visible
    public Map<String, Integer> indexById = new HashMap<>();

    public ActiveShopState(String shopId){
        this.shopId = shopId;
        this.visible = new ArrayList<>();
        this.perPlayerBought = new HashMap<>();
        this.shuffledAt = Instant.now();
    }
}
