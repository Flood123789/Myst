package mystcraft.flood.client.render;

import net.minecraft.client.resource.metadata.AnimationResourceMetadata;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.SpriteContents;
import net.minecraft.client.texture.SpriteDimensions;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.metadata.ResourceMetadata;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.Optional;

public class DynamicGrayscaleTextureGenerator {

    public static NativeImage generateGrayscaleImage(ResourceManager resourceManager, Identifier originalTextureLocation) {
        try {
            Optional<Resource> resourceOpt = resourceManager.getResource(originalTextureLocation);
            if (resourceOpt.isPresent()) {
                try (InputStream stream = resourceOpt.get().getInputStream()) {
                    NativeImage src = NativeImage.read(stream);
                    int width = src.getWidth();
                    int height = src.getHeight();
                    NativeImage grayImage = new NativeImage(src.getFormat(), width, height, false);

                    for (int y = 0; y < height; y++) {
                        for (int x = 0; x < width; x++) {
                            int abgr = src.getColor(x, y);
                            int a = (abgr >> 24) & 0xFF;
                            int b = (abgr >> 16) & 0xFF;
                            int g = (abgr >> 8) & 0xFF;
                            int r = abgr & 0xFF;

                            // Perceptual grayscale conversion with brightness boost for flame highlights
                            int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                            gray = Math.min(255, (int) (gray * 1.35f));

                            int newAbgr = (a << 24) | (gray << 16) | (gray << 8) | gray;
                            grayImage.setColor(x, y, newAbgr);
                        }
                    }
                    src.close();
                    return grayImage;
                }
            }
        } catch (Exception e) {
            System.err.println("[Mystcraft Reforged] Failed to auto-generate grayscale texture for " + originalTextureLocation + ": " + e.getMessage());
        }
        return null;
    }

    public static SpriteContents createGrayscaleSprite(Identifier newSpriteId, Identifier sourceTextureLocation, ResourceManager resourceManager) {
        NativeImage grayImage = generateGrayscaleImage(resourceManager, sourceTextureLocation);
        if (grayImage == null) return null;

        try {
            Optional<Resource> resourceOpt = resourceManager.getResource(sourceTextureLocation);
            ResourceMetadata metadata = resourceOpt.isPresent() ? resourceOpt.get().getMetadata() : ResourceMetadata.NONE;
            AnimationResourceMetadata animMeta = metadata.decode(AnimationResourceMetadata.READER).orElse(AnimationResourceMetadata.EMPTY);

            // Sprite width is equal to image width (for square animated frames 16x16, 32x32, etc)
            int frameWidth = grayImage.getWidth();
            int frameHeight = frameWidth; // single frame size

            SpriteDimensions dimensions = new SpriteDimensions(frameWidth, frameHeight);
            return new SpriteContents(newSpriteId, dimensions, grayImage, animMeta);
        } catch (Exception e) {
            System.err.println("[Mystcraft Reforged] Error building sprite contents for " + newSpriteId + ": " + e.getMessage());
        }
        return null;
    }
}
