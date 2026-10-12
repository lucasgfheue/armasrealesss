package com.example.armasreales;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Pistola de prueba: disparo instantáneo (hitscan) con munición, cooldown y desgaste.
 */
public class PistolItem extends Item {
    private static final float DAMAGE = 8.0f;      // 4 corazones
    private static final double RANGE = 60.0;      // bloques
    private static final int COOLDOWN_TICKS = 10;  // medio segundo entre disparos

    public PistolItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(gun);
        }

        boolean creative = player.getAbilities().instabuild;
        ItemStack ammo = findAmmo(player);

        if (ammo.isEmpty() && !creative) {
            // Sin munición: "clic" y pequeño cooldown
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 1.6f);
            player.getCooldowns().addCooldown(this, 8);
            return InteractionResultHolder.fail(gun);
        }

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            shoot(serverLevel, player);
            if (!creative) {
                ammo.shrink(1);
                gun.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.sidedSuccess(gun, level.isClientSide());
    }

    private void shoot(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(RANGE));

        // 1) ¿Choca con un bloque antes?
        BlockHitResult blockHit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }

        // 2) ¿Choca con una entidad en el camino?
        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                level, player, eye, end, searchBox,
                e -> !e.isSpectator() && e.isPickable());

        if (entityHit != null) {
            Entity target = entityHit.getEntity();
            end = entityHit.getLocation();
            target.hurt(level.damageSources().playerAttack(player), DAMAGE);
        }

        // Efectos: sonido, fogonazo y estela de partículas
        level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 1.5f, 0.7f);

        Vec3 muzzle = eye.add(look.scale(0.8));
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y - 0.1, muzzle.z, 5, 0.03, 0.03, 0.03, 0.01);
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y - 0.1, muzzle.z, 2, 0.01, 0.01, 0.01, 0.0);

        double dist = eye.distanceTo(end);
        for (double d = 2.0; d < dist; d += 1.5) {
            Vec3 p = eye.add(look.scale(d));
            level.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    private ItemStack findAmmo(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.MUNICION_PISTOLA.get())) {
                return stack;
            }
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (stack.is(ModItems.MUNICION_PISTOLA.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
