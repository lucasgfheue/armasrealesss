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

/** Lanzador que dispara bloques de TNT (usa TNT del inventario como munición). */
public class TntLauncherItem extends Item {
    private static final int COOLDOWN_TICKS = 30;  // 1,5 s entre disparos
    private static final float SPEED = 2.0f;

    public TntLauncherItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(gun);
        }

        boolean creative = player.getAbilities().instabuild;
        ItemStack tnt = AmmoUtil.find(player, Items.TNT);

        if (tnt.isEmpty() && !creative) {
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 0.8f);
            player.getCooldowns().addCooldown(this, 10);
            return InteractionResultHolder.fail(gun);
        }

        if (!level.isClientSide) {
            TntProjectile projectile = new TntProjectile(level, player);
            projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, SPEED, 0.8f);
            level.addFreshEntity(projectile);

            level.playSound(null, player.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH,
                    SoundSource.PLAYERS, 1.2f, 0.6f);
            level.playSound(null, player.blockPosition(), SoundEvents.TNT_PRIMED,
                    SoundSource.PLAYERS, 0.8f, 1.0f);

            if (!creative) {
                tnt.shrink(1);
                gun.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            }
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.sidedSuccess(gun, level.isClientSide());
    }
}
