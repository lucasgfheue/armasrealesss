package com.example.armasreales;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Optional;

/**
 * Rifle láser: mantén clic derecho para disparar un rayo rojo continuo.
 * - Atraviesa entidades y daña a todas las que toque (daño continuo + fuego).
 * - Se detiene en los bloques; la arena que toque se convierte en vidrio.
 * - Consume 1 Celda de energía por ráfaga. Dura unos segundos y luego se recalienta.
 */
public class LaserRifleItem extends Item {
    private static final double RANGE = 28.0;        // alcance del rayo en bloques
    private static final int MAX_BEAM_TICKS = 80;    // duración máxima de la ráfaga (4 s)
    private static final int COOLDOWN_TICKS = 60;    // recalentamiento tras la ráfaga (3 s)
    private static final int DAMAGE_INTERVAL = 4;    // cada cuántos ticks hace daño
    private static final float DAMAGE = 2.0f;        // daño por golpe (1 corazón)

    private static final DustParticleOptions RED_DUST =
            new DustParticleOptions(new Vector3f(1.0f, 0.05f, 0.05f), 1.0f);

    public LaserRifleItem(Properties properties) {
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
                    SoundSource.PLAYERS, 1.0f, 1.2f);
            player.getCooldowns().addCooldown(this, 10);
            return InteractionResultHolder.fail(gun);
        }

        if (!level.isClientSide) {
            if (!creative) {
                cell.shrink(1);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE,
                    SoundSource.PLAYERS, 0.8f, 2.0f);
        }

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(gun);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
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

        int elapsed = getUseDuration(stack) - remainingUseDuration;
        if (elapsed > MAX_BEAM_TICKS) {
            player.releaseUsingItem(); // fin de la ráfaga -> releaseUsing aplica el recalentamiento
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
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        }
    }

    private void fireBeam(ServerLevel level, Player player, int elapsed) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(RANGE));

        // El rayo se detiene en el primer bloque sólido
        BlockHitResult blockHit = level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        boolean hitBlock = blockHit.getType() == HitResult.Type.BLOCK;
        if (hitBlock) {
            end = blockHit.getLocation();
        }

        // Daño continuo a TODAS las entidades vivas que cruce el rayo (lo atraviesa)
        if (elapsed % DAMAGE_INTERVAL == 0) {
            AABB searchBox = new AABB(eye, end).inflate(1.0);
            for (Entity target : level.getEntities(player, searchBox,
                    e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
                Optional<Vec3> hit = target.getBoundingBox().inflate(0.3).clip(eye, end);
                if (hit.isPresent()) {
                    target.invulnerableTime = 0; // permite daño rápido y continuo
                    target.hurt(level.damageSources().playerAttack(player), DAMAGE);
                    if (!target.fireImmune()) {
                        target.setRemainingFireTicks(60);
                    }
                }
            }
        }

        // La arena se transforma en vidrio
        if (hitBlock) {
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.SAND)) {
                level.setBlockAndUpdate(pos, Blocks.GLASS.defaultBlockState());
                level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.6f, 1.4f);
                level.sendParticles(ParticleTypes.SMOKE,
                        pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.3, 0.1, 0.3, 0.01);
            }
            level.sendParticles(ParticleTypes.FLAME, end.x, end.y, end.z, 2, 0.05, 0.05, 0.05, 0.01);
        }

        // Dibujo del rayo rojo con partículas
        Vec3 origin = eye.add(look.scale(0.9)).add(0.0, -0.18, 0.0);
        double dist = origin.distanceTo(end);
        for (double d = 0.0; d < dist; d += 0.5) {
            Vec3 p = origin.add(look.scale(d));
            level.sendParticles(RED_DUST, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        if (elapsed % 10 == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.BEACON_AMBIENT,
                    SoundSource.PLAYERS, 0.5f, 2.0f);
        }
    }
}
