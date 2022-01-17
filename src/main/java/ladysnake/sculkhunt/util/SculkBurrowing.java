package ladysnake.sculkhunt.util;

import ladysnake.sculkhunt.cca.BurrowingComponent;
import ladysnake.sculkhunt.cca.SculkhuntComponents;
import ladysnake.sculkhunt.common.init.SculkhuntBlocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

import java.util.Random;

public class SculkBurrowing
{
    public static void updateNoClip(PlayerEntity player)
    {
        BurrowingComponent burrowingComponent = SculkhuntComponents.BURROWING.get(player);
        if (burrowingComponent.getSinkTicks() > 0 || burrowingComponent.isRising() || burrowingComponent.isBurrowing()) player.noClip = true;
    }
    public static void updateMovementSpeed(PlayerEntity player)
    {
        BurrowingComponent burrowingComponent = SculkhuntComponents.BURROWING.get(player);
        if (burrowingComponent.getSinkTicks() > 0 || burrowingComponent.isRising() || burrowingComponent.isBurrowing()) player.setMovementSpeed(0);
    }
    public static void tickBurrowing(PlayerEntity player, Random random)
    {
        World world = player.world;
        BurrowingComponent burrowingComponent = SculkhuntComponents.BURROWING.get(player);

        if (burrowingComponent.getSinkTicks() > 0)
        {
            player.setVelocity(0, -player.getHeight() * 0.05, 0);
            player.velocityModified = true;
            player.velocityDirty = true;
            startOrEndBurrowEffect(player, random);

            burrowingComponent.decrementSinkTicks();
            if (burrowingComponent.getSinkTicks() == 0) burrowingComponent.setBurrowing(true);
        }
        else if (burrowingComponent.isRising())
        {
            player.setVelocity(0, world.getBlockState(player.getBlockPos().up()).isSolidBlock(world, player.getBlockPos().up()) ? 1 : 0.05, 0);
            player.velocityModified = true;
            player.velocityDirty = true;
            startOrEndBurrowEffect(player, random);
            if (!world.getBlockState(player.getBlockPos()).isSolidBlock(world, player.getBlockPos())) burrowingComponent.endBurrowing();
        }

        if (burrowingComponent.isBurrowing())
        {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 21, 0, false, false, false));

            if (!burrowingComponent.isRising())
            {
                GameOptions options = MinecraftClient.getInstance().options;
                if (options.keyJump.isPressed())
                {
                    player.setVelocity(player.getVelocity().x, player.getHeight() * 0.05, player.getVelocity().z);
                    player.velocityModified = true;
                    player.velocityDirty = true;
                }
                else if (options.keySneak.isPressed())
                {
                    player.setVelocity(player.getVelocity().x, -player.getHeight() * 0.05, player.getVelocity().z);
                    player.velocityModified = true;
                    player.velocityDirty = true;
                }
                else
                {
                    player.setVelocity(player.getVelocity().x, 0, player.getVelocity().z);
                    player.velocityModified = true;
                    player.velocityDirty = true;
                }

                if (player.getVelocity().lengthSquared() > 0) player.playSound(SoundEvents.BLOCK_SCULK_SENSOR_STEP, 0.25f, 0.9f);
                if (!world.getBlockState(player.getBlockPos()).isSolidBlock(world, player.getBlockPos())) burrowingComponent.endBurrowing();
            }
        }
    }

    private static void startOrEndBurrowEffect(PlayerEntity player, Random random)
    {
        for (int i = 0; i < (player.getWidth() * player.getHeight()) * 25; i++) {
            player.world.addParticle(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(SculkhuntBlocks.SCULK)), player.getX() + random.nextGaussian() * player.getWidth() / 5f, player.getY() + random.nextGaussian() * player.getHeight() / 5f, player.getZ() + random.nextGaussian() * player.getWidth() / 5f, random.nextGaussian() / 10f, random.nextFloat() / 5f, random.nextGaussian() / 10f);
        }
        player.playSound(SoundEvents.BLOCK_SCULK_SENSOR_STEP, 1.0f, 0.9f);
    }
}
