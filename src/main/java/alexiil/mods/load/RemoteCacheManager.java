package alexiil.mods.load;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;

import alexiil.mods.load.imgur.LateInitDynamicTexture;

public abstract class RemoteCacheManager<C extends AutoCloseable> {

    private static final boolean OFFLINE_MODE = Boolean.getBoolean("bls.offlineMode");

    private final String cacheDir;
    private final String providerName;
    private final Map<String, AbstractTexture> textureCache = new ConcurrentHashMap<>();
    private volatile boolean cancelSetup;

    protected RemoteCacheManager(String cacheDir, String providerName) {
        this.cacheDir = cacheDir;
        this.providerName = providerName;
    }

    public abstract void loadConfig(Configuration config);

    protected abstract C createClient() throws Exception;

    protected abstract List<ImageEntry> fetchRemoteImages(C client) throws Exception;

    protected abstract byte[] downloadImage(C client, ImageEntry entry) throws Exception;

    public AbstractTexture getCachedTexture(ResourceLocation location) {
        if (!location.getResourceDomain().equals(cacheDir)) return null;

        return textureCache.get(location.getResourcePath());
    }

    public void cleanUp() {
        textureCache.values().forEach(AbstractTexture::deleteGlTexture);
        textureCache.clear();

        cancelSetup = true;
    }

    public void setup(Consumer<ResourceLocation> textureLocationConsumer) {
        Path cacheFolder = Paths.get(cacheDir);
        if (Files.notExists(cacheFolder)) {
            try {
                Files.createDirectory(cacheFolder);
            } catch (IOException e) {
                BetterLoadingScreen.log.error("Error while creating " + providerName + " cache directory", e);
                return;
            }
        }

        List<String> cachedImageIDs = getCachedImageIDs();
        if (cachedImageIDs == null) return;

        // Load any image that is already cached to get something rendering quickly
        loadAnyImageFromDisk(cachedImageIDs, textureLocationConsumer);

        CompletableFuture.runAsync(() -> {
            try (C client = OFFLINE_MODE ? null : createClient()) {
                Consumer<ImageEntry> imageHandler = entry -> {
                    String imageID = entry.cacheId;

                    synchronized (cachedImageIDs) {
                        cachedImageIDs.remove(imageID);
                    }

                    if (cancelSetup) return;

                    if (textureCache.containsKey(imageID)) return;

                    Path imageFile = getCachedImagePath(imageID);

                    try {
                        if (Files.exists(imageFile)) {
                            readAndCacheImageFromStream(
                                    imageID,
                                    new BufferedInputStream(Files.newInputStream(imageFile), 1024 * 1024),
                                    false);
                        } else {
                            if (OFFLINE_MODE) return;

                            readAndCacheImageFromStream(
                                    imageID,
                                    new ByteArrayInputStream(downloadImage(client, entry)),
                                    true);
                        }
                    } catch (Exception e) {
                        BetterLoadingScreen.log.error("Error while loading " + providerName + " image", e);
                        return;
                    }

                    synchronized (textureLocationConsumer) {
                        textureLocationConsumer.accept(new ResourceLocation(cacheDir, imageID));
                    }
                };

                if (OFFLINE_MODE) {
                    cachedImageIDs.stream().parallel().map(id -> new ImageEntry(id, id)).forEach(imageHandler);
                } else {
                    fetchRemoteImages(client).stream().parallel().forEach(imageHandler);
                }
            } catch (Exception e) {
                BetterLoadingScreen.log.error("Error while fetching " + providerName + " images", e);
            }
        }).thenRunAsync(() -> {
            if (OFFLINE_MODE) return;

            // Delete cached images that are no longer in the album
            try {
                for (String id : cachedImageIDs) {
                    Files.deleteIfExists(getCachedImagePath(id));
                }
            } catch (IOException e) {
                BetterLoadingScreen.log.error("Error while deleting unused cached " + providerName + " images", e);
            }
        });
    }

    private void loadAnyImageFromDisk(List<String> cachedImageIDs, Consumer<ResourceLocation> textureLocationConsumer) {
        if (cachedImageIDs.isEmpty()) return;

        String imageID = cachedImageIDs.get(ThreadLocalRandom.current().nextInt(cachedImageIDs.size()));
        try {
            readAndCacheImageFromDisk(imageID);
        } catch (IOException e) {
            BetterLoadingScreen.log.error("Error while loading first cached " + providerName + " image", e);
            return;
        }

        synchronized (textureLocationConsumer) {
            textureLocationConsumer.accept(new ResourceLocation(cacheDir, imageID));
        }
    }

    private void readAndCacheImageFromStream(String imageID, InputStream imageStream, boolean saveToDisk)
            throws IOException {
        BufferedImage image = ImageIO.read(imageStream);
        textureCache.put(imageID, new LateInitDynamicTexture(image, image.getWidth(), image.getHeight()));

        if (saveToDisk && Files.notExists(getCachedImagePath(imageID))) writeImageToCache(imageID, image);
    }

    private void readAndCacheImageFromDisk(String imageID) throws IOException {
        readAndCacheImageFromStream(
                imageID,
                new BufferedInputStream(Files.newInputStream(getCachedImagePath(imageID)), 1024 * 1024),
                false);
    }

    private void writeImageToCache(String imageID, BufferedImage image) throws IOException {
        ImageIO.write(
                image,
                "png",
                new BufferedOutputStream(Files.newOutputStream(getCachedImagePath(imageID)), 1024 * 1024));
    }

    private Path getCachedImagePath(String imageID) {
        return Paths.get(cacheDir).resolve(imageID + ".png");
    }

    private List<String> getCachedImageIDs() {
        try (Stream<Path> cacheFolderStream = Files.list(Paths.get(cacheDir))) {
            return cacheFolderStream.map(path -> path.getFileName().toString().replace(".png", ""))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            BetterLoadingScreen.log.error("Error while iterating " + providerName + " cache folder", e);
            return null;
        }
    }

    public static class ImageEntry {

        public final String cacheId;
        public final String downloadRef;

        public ImageEntry(String cacheId, String downloadRef) {
            this.cacheId = cacheId;
            this.downloadRef = downloadRef;
        }
    }
}
