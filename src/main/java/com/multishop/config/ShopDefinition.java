package com.multishop.config;

import java.util.List;

public class ShopDefinition {
    public String id;
    public String displayName;
    public String command;
    public Integer maxPoolSize; // optional
    public int visibleSlots;
    public List<ShopItemDef> items;

    public String toString(){ return "ShopDefinition(" + id + ")"; }
}
