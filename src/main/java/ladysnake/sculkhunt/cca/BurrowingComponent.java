package ladysnake.sculkhunt.cca;

import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

public class BurrowingComponent implements AutoSyncedComponent
{
    private PlayerEntity player;
    private boolean burrowing;
    private boolean rising;
    private int sinkTicks;

    public BurrowingComponent(PlayerEntity player)
    {
        this.player = player;
        burrowing = false;
        rising = false;
        sinkTicks = 0;
    }

    public boolean isBurrowing() { return burrowing; }
    public boolean isRising() { return rising; }
    public int getSinkTicks() { return sinkTicks; }

    public void startBurrowing()
    {
        sinkTicks = 20;
        rising = false;
        SculkhuntComponents.BURROWING.sync(player);
    }
    public void startRise()
    {
        sinkTicks = 0;
        rising = true;
        SculkhuntComponents.BURROWING.sync(player);
    }
    public void toggleBurrow(BlockPos sculkLocation)
    {
        if (sinkTicks > 0 || burrowing) startRise();
        else startBurrowing();
    }

    public void setBurrowing(boolean burrowing)
    {
        this.burrowing = burrowing;
        SculkhuntComponents.BURROWING.sync(player);
    }
    public void endBurrowing()
    {
        this.rising = false;
        this.burrowing = false;
        player.removeStatusEffect(StatusEffects.BLINDNESS);
        SculkhuntComponents.BURROWING.sync(player);
    }
    public void decrementSinkTicks()
    {
        if (sinkTicks > 0)
        {
            this.sinkTicks--;
            SculkhuntComponents.BURROWING.sync(player);
        }
    }

    @Override
    public void readFromNbt(NbtCompound tag)
    {
        burrowing = tag.getBoolean("IsBurrowing");
        rising = tag.getBoolean("IsRising");
        sinkTicks = tag.getInt("SinkTicks");
    }

    @Override
    public void writeToNbt(NbtCompound tag)
    {
        tag.putBoolean("IsBurrowing", burrowing);
        tag.putBoolean("IsRising", rising);
        tag.putInt("SinkTicks", sinkTicks);
    }
}
