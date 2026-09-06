package alexiil.mods.load;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

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

    private final List<ResourceTexture> ownedTextures = new ArrayList<>();

    public SplashTextureManager(IResourceManager resources) {
        super(resources);
    }

    @Override
    public void bindTexture(ResourceLocation location) {
        if (getTexture(location) == null) {
            ResourceTexture texture = new ResourceTexture(location);
            ownedTextures.add(texture);
            loadTexture(location, texture);
        }
        super.bindTexture(location);
    }

    public void close() {
        ownedTextures.forEach(AbstractTexture::deleteGlTexture);
        ownedTextures.clear();
    }

    public static void upload(int textureId, int[] pixels, int width, int height, boolean blur, boolean clamp) {
        // Minecraft's upload buffer is shared with the loading thread.
        int rowsPerUpload = Math.min(height, Math.max(1, 1024 * 1024 / width));
        IntBuffer buffer = BufferUtils.createIntBuffer(width * rowsPerUpload);
        if (Minecraft.getMinecraft().gameSettings.anaglyph) pixels = TextureUtil.updateAnaglyph(pixels);
        TextureUtil.allocateTexture(textureId, width, height);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, blur ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, blur ? GL11.GL_LINEAR : GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, clamp ? GL11.GL_CLAMP : GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, clamp ? GL11.GL_CLAMP : GL11.GL_REPEAT);
        for (int y = 0; y < height; y += rowsPerUpload) {
            int rows = Math.min(rowsPerUpload, height - y);
            buffer.clear();
            buffer.put(pixels, y * width, rows * width);
            buffer.flip();
            GL11.glTexSubImage2D(
                    GL11.GL_TEXTURE_2D,
                    0,
                    0,
                    y,
                    width,
                    rows,
                    GL12.GL_BGRA,
                    GL12.GL_UNSIGNED_INT_8_8_8_8_REV,
                    buffer);
        }
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
