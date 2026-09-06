package alexiil.mods.load.imgur;

import java.awt.image.BufferedImage;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.IResourceManager;

import alexiil.mods.load.SplashTextureManager;

/**
 * This class is basically like {@link net.minecraft.client.renderer.texture.DynamicTexture}, but it doesn't allocate
 * and upload the texture until {@link #loadTexture(IResourceManager)} is called.
 */
public class LateInitDynamicTexture extends AbstractTexture {

    private final int[] dynamicTextureData;
    private final int width;
    private final int height;

    public LateInitDynamicTexture(BufferedImage image, int width, int height) {
        this.width = width;
        this.height = height;
        this.dynamicTextureData = new int[width * height];
        image.getRGB(0, 0, width, height, this.dynamicTextureData, 0, width);
    }

    public void loadTexture(IResourceManager rs) {
        if (this.glTextureId != -1) return;

        SplashTextureManager
                .upload(this.getGlTextureId(), this.dynamicTextureData, this.width, this.height, false, false);
    }
}
