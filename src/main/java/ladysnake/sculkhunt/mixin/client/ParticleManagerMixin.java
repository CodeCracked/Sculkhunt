package ladysnake.sculkhunt.mixin.client;

import com.mojang.blaze3d.systems.RenderSystem;
import ladysnake.sculkhunt.client.particle.SculkhuntParticleTextureSheet;
import ladysnake.sculkhunt.client.particle.SoundParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import net.minecraft.util.crash.CrashReportSection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Queue;

@Mixin(ParticleManager.class)
public class ParticleManagerMixin
{
    private static final ParticleTextureSheet[] CUSTOM_PARTICLE_TEXTURE_SHEETS = new ParticleTextureSheet[] { SculkhuntParticleTextureSheet.SOUND_PARTICLE_SHEET };

    @Shadow
    @Final
    private Map<ParticleTextureSheet, Queue<Particle>> particles;

    @Shadow
    @Final
    private TextureManager textureManager;

    @Inject(method = "renderParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V", shift = At.Shift.BEFORE))
    public void renderParticles(MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers, LightmapTextureManager lightmapTextureManager, Camera camera, float tickDelta, CallbackInfo callback)
    {
        for (ParticleTextureSheet particleTextureSheet : CUSTOM_PARTICLE_TEXTURE_SHEETS)
        {
            Queue<Particle> iterable = this.particles.get(particleTextureSheet);
            if (iterable == null) continue;
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder bufferBuilder = tessellator.getBuffer();
            particleTextureSheet.begin(bufferBuilder, this.textureManager);
            for (Particle particle : iterable)
            {
                try
                {
                    particle.buildGeometry(bufferBuilder, camera, tickDelta);
                }
                catch (Throwable throwable) {
                    CrashReport crashReport = CrashReport.create(throwable, "Rendering Particle");
                    CrashReportSection crashReportSection = crashReport.addElement("Particle being rendered");
                    crashReportSection.add("Particle", particle::toString);
                    crashReportSection.add("Particle Type", particleTextureSheet::toString);
                    throw new CrashException(crashReport);
                }
            }
            particleTextureSheet.draw(tessellator);
            if (particleTextureSheet instanceof SculkhuntParticleTextureSheet extendedSheet) extendedSheet.end(bufferBuilder, this.textureManager);
        }
    }
}
