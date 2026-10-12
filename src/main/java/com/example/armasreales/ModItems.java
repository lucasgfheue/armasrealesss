package com.example.armasreales;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(ArmasRealesMod.MOD_ID);

    public static final DeferredItem<PistolItem> PISTOLA = ITEMS.registerItem(
            "pistola",
            PistolItem::new,
            new Item.Properties().stacksTo(1).durability(500));

    public static final DeferredItem<Item> MUNICION_PISTOLA = ITEMS.registerItem(
            "municion_pistola",
            Item::new,
            new Item.Properties());
}
