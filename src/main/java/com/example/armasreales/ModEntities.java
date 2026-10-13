package com.example.armasreales;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, ArmasRealesMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<TntProjectile>> TNT_PROJECTILE =
            ENTITIES.register("proyectil_tnt", () ->
                    EntityType.Builder.<TntProjectile>of(TntProjectile::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(4)
                            .updateInterval(10)
                            .build("proyectil_tnt"));
}
