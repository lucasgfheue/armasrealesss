package com.example.armasreales;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/** Pistola de bengalas: dispara una bengala gigante. Usa Bolas de fuego como munición. */
public class FlareGunItem extends Item {
    private static final int COOLDOWN_TICKS = 40;  // 2 s entre disparos
    private static final float SPEED = 1.6f;

    public FlareGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(gun);
        }

        boolean creative = player.getAbilities().instabuild;
        ItemStack ammo = AmmoUtil.find(player, Items.FIRE_CHARGE);

        if (ammo.isEmpty() && !creative) {
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 1.0f);
            player.getCooldowns().addCooldown(this, 10);
            return InteractionResultHolder.fail(gun);
        }

        if (!level.isClientSide) {
            FlareProjectile flare = new FlareProjectile(level, player);
            // La caja de colisión mide 1x1: la centramos a la altura de los ojos, un poco adelante
            net.minecraft.world.phys.Vec3 look = player.getLookAngle();
            flare.setPos(player.getX() + look.x * 0.8,
                    player.getEyeY() - 0.6 + look.y * 0.8,
                    player.getZ() + look.z * 0.8);
            flare.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, SPEED, 0.4f);
            level.addFreshEntity(flare);

            level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH,
                    SoundSource.PLAYERS, 1.2f, 0.8f);
            level.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT,
                    SoundSource.PLAYERS, 0.8f, 1.2f);

            if (!creative) {
                ammo.shrink(1);
                gun.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.sidedSuccess(gun, level.isClientSide());
    }
}
