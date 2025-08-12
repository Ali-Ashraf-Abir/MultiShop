package com.multishop.config;

import java.util.List;

public class ShopItemDef {
    public String id;
    public String type;          // VANILLA | CUSTOM
    public String material;      // VANILLA
    public String icon;          // CUSTOM display icon
    public String displayName;   // optional
    public List<String> lore;    // optional

    public int price;
    public int chance;           // 0..100
    public int maxPerPlayer;     // per cycle

    public Integer minStock;     // nullable
    public Integer maxStock;     // nullable
    public boolean unlimitedStock; // NEW

    public String giveCommand;   // CUSTOM only
    public String executeAs;     // "CONSOLE" | "PLAYER" (default CONSOLE)
}
