package ladysnake.sculkhunt.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.texture.TextureManager;
import org.lwjgl.opengl.GL11;

public interface SculkhuntParticleTextureSheet extends ParticleTextureSheet
{
    void end(BufferBuilder builder, TextureManager textureManager);

    ParticleTextureSheet SOUND_PARTICLE_SHEET = new SculkhuntParticleTextureSheet()
    {
        private float fogStartRestore;

        @Override
        public void begin(BufferBuilder builder, TextureManager textureManager)
        {
            RenderSystem.disableDepthTest();
            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            fogStartRestore = RenderSystem.getShaderFogStart();
            BackgroundRenderer.clearFog();

            RenderSystem.depthMask(true);
            RenderSystem.setShaderTexture(0, SpriteAtlasTexture.PARTICLE_ATLAS_TEXTURE);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SrcFactor.SRC_ALPHA, GlStateManager.DstFactor.ONE_MINUS_SRC_ALPHA);
            builder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR_LIGHT);
        }

        @Override
        public void end(BufferBuilder builder, TextureManager textureManager)
        {
            RenderSystem.setShaderFogStart(fogStartRestore);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LESS);
        }

        @Override
        public void draw(Tessellator tessellator)
        {
            tessellator.draw();
        }

        @Override
        public String toString()
        {
            return "SOUND_PARTICLE_SHEET";
        }
    };
}
