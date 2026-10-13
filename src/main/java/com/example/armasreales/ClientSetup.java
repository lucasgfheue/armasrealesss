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
        // TNT: bloque de TNT (tamaño x2)
        event.registerEntityRenderer(ModEntities.TNT_PROJECTILE.get(),
                context -> new ThrownItemRenderer<>(context, 2.0F, false));

        // Bengala: bola de fuego gigante con halo brillante
        event.registerEntityRenderer(ModEntities.FLARE_PROJECTILE.get(), FlareRenderer::new);

        // Rayos del láser y del plasma
        event.registerEntityRenderer(ModEntities.BEAM.get(), BeamRenderer::new);
    }
}
