package alexiil.mods.load;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.TextureMetadataSection;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public class SplashTextureManager extends TextureManager {

    public SplashTextureManager(IResourceManager resources) {
        super(resources);
    }

    @Override
    public void bindTexture(ResourceLocation location) {
        if (getTexture(location) == null) loadTexture(location, new ResourceTexture(location));
        super.bindTexture(location);
    }

    public static void upload(int textureId, int[] pixels, int width, int height, boolean blur, boolean clamp) {
        // Minecraft's upload buffer is shared with the loading thread.
        IntBuffer buffer = BufferUtils.createIntBuffer(pixels.length);
        buffer.put(Minecraft.getMinecraft().gameSettings.anaglyph ? TextureUtil.updateAnaglyph(pixels) : pixels);
        buffer.flip();
        TextureUtil.allocateTexture(textureId, width, height);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, blur ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, blur ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, clamp ? GL11.GL_CLAMP : GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, clamp ? GL11.GL_CLAMP : GL11.GL_REPEAT);
        GL11.glTexSubImage2D(
                GL11.GL_TEXTURE_2D,
                0,
                0,
                0,
                width,
                height,
                GL12.GL_BGRA,
                GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                buffer);
    }

    private static class ResourceTexture extends AbstractTexture {

        private final ResourceLocation location;

        private ResourceTexture(ResourceLocation location) {
            this.location = location;
        }

        @Override
        public void loadTexture(IResourceManager resources) throws IOException {
            IResource resource = resources.getResource(location);
            BufferedImage image;
            try (InputStream stream = resource.getInputStream()) {
                image = ImageIO.read(stream);
            }
            if (image == null) throw new IOException("Invalid splash image: " + location);
            TextureMetadataSection metadata = null;
            try {
                metadata = (TextureMetadataSection) resource.getMetadata("texture");
            } catch (RuntimeException e) {
                BetterLoadingScreen.log.warn("Failed reading texture metadata: " + location, e);
            }
            int width = image.getWidth();
            int height = image.getHeight();
            upload(
                    getGlTextureId(),
                    image.getRGB(0, 0, width, height, null, 0, width),
                    width,
                    height,
                    metadata != null && metadata.getTextureBlur(),
                    metadata != null && metadata.getTextureClamp());
        }
    }
}
