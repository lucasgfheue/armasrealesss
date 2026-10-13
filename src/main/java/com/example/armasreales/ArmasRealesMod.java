package com.example.armasreales;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;

@Mod(ArmasRealesMod.MOD_ID)
public class ArmasRealesMod {
    public static final String MOD_ID = "armasreales";

    public ArmasRealesMod(IEventBus modBus) {
        ModItems.ITEMS.register(modBus);
        ModEntities.ENTITIES.register(modBus);
        modBus.addListener(this::addToCreativeTabs);
    }

    private void addToCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.PISTOLA);
            event.accept(ModItems.MUNICION_PISTOLA);
            event.accept(ModItems.RIFLE_LASER);
            event.accept(ModItems.CELDA_ENERGIA);
            event.accept(ModItems.LANZADOR_TNT);
        }
    }
}
