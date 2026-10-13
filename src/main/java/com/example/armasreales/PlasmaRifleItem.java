package com.example.armasreales;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Optional;
import java.util.function.Predicate;

/**
 * Rifle de plasma: mantén clic derecho para disparar un rayo continuo durante 6 segundos,
 * después se sobrecalienta. El rayo golpea a la primera entidad que toque y la descarga
 * "eléctrica" salta a las entidades que estén a su alrededor.
 * Consume 1 Celda de energía por ráfaga.
 */
public class PlasmaRifleItem extends Item {
    private static final double RANGE = 26.0;
    private static final int MAX_BEAM_TICKS = 120;       // 6 segundos
    private static final int MAX_COOLDOWN_TICKS = 200;   // sobrecalentamiento: 10 segundos
    private static final int DAMAGE_INTERVAL = 5;        // cada cuántos ticks golpea
    private static final float DAMAGE = 3.0f;            // golpe directo
    private static final float CHAIN_DAMAGE = 2.0f;      // golpe de la descarga a los cercanos
    private static final double CHAIN_RADIUS = 4.5;      // radio de la descarga
    private static final int MAX_CHAIN_TARGETS = 6;

    private static final Predicate<Entity> TARGETS =
            e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator();

    public PlasmaRifleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gun = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(gun);
        }

        boolean creative = player.getAbilities().instabuild;
        ItemStack cell = AmmoUtil.find(player, ModItems.CELDA_ENERGIA.get());

        if (cell.isEmpty() && !creative) {
            level.playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 1.0f, 1.4f);
            player.getCooldowns().addCooldown(this, 10);
            return InteractionResultHolder.fail(gun);
        }

        if (!level.isClientSide) {
            if (!creative) {
                cell.shrink(1);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS, 0.8f, 1.3f);
        }

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(gun);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof Player player)) {
            return;
        }

        int elapsed = getUseDuration(stack, entity) - remainingUseDuration;

        if (elapsed >= MAX_BEAM_TICKS) {
            overheat(serverLevel, player);
            player.releaseUsingItem();
            return;
        }

        fireBeam(serverLevel, player, elapsed);

        if (elapsed % 20 == 0 && !player.getAbilities().instabuild) {
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(player.getUsedItemHand()));
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        if (entity instanceof Player player) {
            int used = getUseDuration(stack, entity) - timeCharged;
            int cooldown = Math.min(MAX_COOLDOWN_TICKS, 20 + used * 3 / 2);
            player.getCooldowns().addCooldown(this, cooldown);
        }
    }

    private void overheat(ServerLevel level, Player player) {
        Vec3 origin = player.getEyePosition().add(player.getLookAngle().scale(0.9)).add(0.0, -0.2, 0.0);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, origin.x, origin.y, origin.z, 20, 0.25, 0.25, 0.25, 0.04);
        level.sendParticles(ParticleTypes.FLAME, origin.x, origin.y, origin.z, 6, 0.15, 0.15, 0.15, 0.03);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXTINGUISH_FIRE,
                SoundSource.PLAYERS, 1.2f, 0.8f);
        player.displayClientMessage(Component.translatable("message.armasreales.sobrecalentado"), true);
    }

    private void fireBeam(ServerLevel level, Player player, int elapsed) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(RANGE));

        // Primero, el bloque más cercano
        BlockHitResult blockHit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }

        // Luego, la primera entidad que el rayo toque antes de ese bloque
        Entity primary = null;
        Vec3 primaryHit = null;
        double best = Double.MAX_VALUE;
        AABB searchBox = new AABB(eye, end).inflate(1.0);
        for (Entity e : level.getEntities(player, searchBox, TARGETS)) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(0.3).clip(eye, end);
            if (hit.isPresent()) {
                double dist = eye.distanceToSqr(hit.get());
                if (dist < best) {
                    best = dist;
                    primary = e;
                    primaryHit = hit.get();
                }
            }
        }
        if (primary != null) {
            end = primaryHit;
        }

        // Golpe directo + descarga eléctrica a las entidades de alrededor
        if (primary != null && elapsed % DAMAGE_INTERVAL == 0) {
            zap(level, player, primary, DAMAGE);

            Vec3 center = primary.getBoundingBox().getCenter();
            AABB area = primary.getBoundingBox().inflate(CHAIN_RADIUS);
            int count = 0;
            for (Entity other : level.getEntities(player, area, TARGETS)) {
                if (other == primary) continue;
                if (other.distanceToSqr(primary) > CHAIN_RADIUS * CHAIN_RADIUS) continue;
                if (count >= MAX_CHAIN_TARGETS) break;
                count++;
                zap(level, player, other, CHAIN_DAMAGE);
                drawArc(level, center, other.getBoundingBox().getCenter());
            }

            level.playSound(null, primary.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT,
                    SoundSource.PLAYERS, 0.2f, 2.0f);
        }

        // Dibujo del rayo de plasma
        Vec3 origin = eye.add(look.scale(0.9)).add(0.0, -0.18, 0.0);
        BeamEntity.update(level, player, BeamEntity.STYLE_PLASMA, (float) eye.distanceTo(end));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 3, 0.15, 0.15, 0.15, 0.05);

        // Avisos de que se está calentando (últimos 1,5 s)
        if (elapsed > MAX_BEAM_TICKS - 30 && elapsed % 3 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, origin.x, origin.y, origin.z, 2, 0.05, 0.05, 0.05, 0.01);
        }

        if (elapsed % 10 == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT,
                    SoundSource.PLAYERS, 0.5f, 1.4f);
        }
    }

    private void zap(ServerLevel level, Player player, Entity target, float damage) {
        target.invulnerableTime = 0;
        target.hurt(level.damageSources().playerAttack(player), damage);
    }

    /** Dibuja un arco eléctrico irregular entre dos puntos. */
    private void drawArc(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        int steps = Math.max(2, (int) (delta.length() / 0.4));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3 p = from.add(delta.scale(t));
            double jitter = (i == 0 || i == steps) ? 0.0 : 0.25;
            double jx = (level.random.nextDouble() - 0.5) * 2.0 * jitter;
            double jy = (level.random.nextDouble() - 0.5) * 2.0 * jitter;
            double jz = (level.random.nextDouble() - 0.5) * 2.0 * jitter;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x + jx, p.y + jy, p.z + jz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
