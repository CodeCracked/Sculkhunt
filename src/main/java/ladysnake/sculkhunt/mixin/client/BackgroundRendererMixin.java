package ladysnake.sculkhunt.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import ladysnake.sculkhunt.cca.SculkhuntComponents;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.CameraSubmersionType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin
{
    private static final float TRACKER_BLINDNESS_DISTANCE = 16.0f;

    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void applyFog(Camera camera, BackgroundRenderer.FogType fogType, float viewDistance, boolean thickFog, CallbackInfo ci)
    {
        CameraSubmersionType cameraSubmersionType = camera.getSubmersionType();
        Entity entity = camera.getFocusedEntity();
        if (cameraSubmersionType != CameraSubmersionType.LAVA && cameraSubmersionType != CameraSubmersionType.WATER)
        {
            if (entity instanceof PlayerEntity player)
            {
                if (SculkhuntComponents.SCULK.get(player).isSculk() && player.hasStatusEffect(StatusEffects.BLINDNESS))
                {
                    int duration = ((LivingEntity)entity).getStatusEffect(StatusEffects.BLINDNESS).getDuration();
                    float endDistance = MathHelper.lerp(Math.min(1.0f, (float)duration / 20.0f), viewDistance, TRACKER_BLINDNESS_DISTANCE);
                    if (fogType == BackgroundRenderer.FogType.FOG_SKY)
                    {
                        RenderSystem.setShaderFogStart(0.0f);
                        RenderSystem.setShaderFogEnd(endDistance * 0.8f);
                    }
                    else
                    {
                        RenderSystem.setShaderFogStart(endDistance * 0.25f);
                        RenderSystem.setShaderFogEnd(endDistance);
                    }
                }
            }
        }
    }
}
