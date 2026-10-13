package com.example.armasreales;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = ArmasRealesMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // El proyectil se dibuja como un bloque de TNT (tamaño x2)
        event.registerEntityRenderer(ModEntities.TNT_PROJECTILE.get(),
                context -> new ThrownItemRenderer<>(context, 2.0F, false));
    }
}
