package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Bengala gigante: vuela recto, brilla en la oscuridad (deja un bloque de luz invisible
 * siguiéndola) y prende fuego a la entidad que golpea.
 */
public class FlareProjectile extends ThrowableItemProjectile {
    private static final float DIRECT_DAMAGE = 4.0f;   // 2 corazones
    private static final int FIRE_TICKS = 160;         // 8 segundos ardiendo
    private static final int MAX_LIFE_TICKS = 80;      // se apaga a los 4 segundos

    private BlockPos lightPos = null;

    public FlareProjectile(EntityType<? extends FlareProjectile> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    public FlareProjectile(Level level, LivingEntity owner) {
        super(ModEntities.FLARE_PROJECTILE.get(), owner, level);
        this.setNoGravity(true);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.FIRE_CHARGE;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isRemoved()) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (this.tickCount > MAX_LIFE_TICKS) {
            clearLight();
            serverLevel.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 12, 0.3, 0.3, 0.3, 0.02);
            this.discard();
            return;
        }

        // Luz que acompaña a la bengala
        BlockPos pos = this.blockPosition().immutable();
        if (!pos.equals(lightPos)) {
            clearLight();
            if (serverLevel.getBlockState(pos).isAir()) {
                serverLevel.setBlock(pos, Blocks.LIGHT.defaultBlockState(), 2);
                lightPos = pos;
            }
        }

        // Estela de fuego
        serverLevel.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 4, 0.2, 0.2, 0.2, 0.02);
        serverLevel.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0.15, 0.15, 0.15, 0.01);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide) {
            return;
        }
        Entity target = result.getEntity();
        target.hurt(this.damageSources().thrown(this, this.getOwner()), DIRECT_DAMAGE);
        if (!target.fireImmune()) {
            target.setRemainingFireTicks(FIRE_TICKS);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level().isClientSide) {
            return;
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLAME, getX(), getY(), getZ(), 30, 0.5, 0.5, 0.5, 0.08);
            serverLevel.sendParticles(ParticleTypes.LAVA, getX(), getY(), getZ(), 8, 0.4, 0.4, 0.4, 0.0);
        }
        this.level().playSound(null, this.blockPosition(), SoundEvents.FIRECHARGE_USE,
                SoundSource.NEUTRAL, 1.0f, 0.8f);
        clearLight();
        this.discard();
    }

    private void clearLight() {
        if (lightPos != null && !this.level().isClientSide) {
            Level level = this.level();
            if (level.getBlockState(lightPos).is(Blocks.LIGHT)) {
                level.setBlock(lightPos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        lightPos = null;
    }
}
