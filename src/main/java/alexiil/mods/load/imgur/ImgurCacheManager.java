package alexiil.mods.load.imgur;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;

import alexiil.mods.load.BetterLoadingScreen;

public class ImgurCacheManager {

    private static final boolean OFFLINE_MODE = Boolean.getBoolean("bls.offlineMode");
    private static final String IMGUR_CACHE_DIR = "bls-imgur-cache";

    private final Map<String, AbstractTexture> textureCache = new ConcurrentHashMap<>();

    private String appClientId;
    private String galleryId;
    private int requestTimeout;

    private volatile boolean cancelSetup;

    public void loadConfig(Configuration config) {
        appClientId = config.getString(
                "imgurAppClientId",
                "imgur",
                "",
                "The client ID of your imgur application. Required to access the imgur api.");
        galleryId = config
                .getString("imgurGalleryId", "imgur", "", "ID of the imgur gallery/album. For example: Ks0TrYE");
        requestTimeout = config.getInt(
                "imgurRequestTimeout",
                "imgur",
                5000,
                100,
                Integer.MAX_VALUE,
                "Request timeout (ms) for imgur requests");
    }

    public AbstractTexture getCachedTexture(ResourceLocation location) {
        if (!location.getResourceDomain().equals(IMGUR_CACHE_DIR)) return null;

        return textureCache.get(location.getResourcePath());
    }

    public synchronized void cleanUp() {
        cancelSetup = true;
        textureCache.values().forEach(AbstractTexture::deleteGlTexture);
        textureCache.clear();
    }

    public void setupImgurGallery(Consumer<ResourceLocation> textureLocationConsumer) {
        Path cacheFolder = Paths.get(IMGUR_CACHE_DIR);
        if (Files.notExists(cacheFolder)) {
            try {
                Files.createDirectory(cacheFolder);
            } catch (IOException e) {
                BetterLoadingScreen.log.error("Error while creating imgur cache directory", e);
            }
        }

        List<String> cachedImageIDs = getCachedImageIDs();

        CompletableFuture.runAsync(() -> {
            // Try cached images before contacting Imgur, without blocking startup on disk reads.
            loadAnyImageFromDisk(cachedImageIDs, textureLocationConsumer);
            if (cancelSetup) return;
            try (ImgurClient client = OFFLINE_MODE ? null : new ImgurClient(appClientId, requestTimeout)) {
                Consumer<String> imageHandler = imageID -> {
                    // This will leave behind cached images that are no longer in the gallery
                    if (!OFFLINE_MODE) {
                        synchronized (cachedImageIDs) {
                            cachedImageIDs.remove(imageID);
                        }
                    }

                    if (cancelSetup) return;

                    // Should only be the image that might have been loaded in loadAnyImageFromDisk()
                    if (textureCache.containsKey(imageID)) return;

                    Path imageFile = getCachedImagePath(imageID);

                    try {
                        boolean loadedFromDisk = false;
                        if (Files.exists(imageFile)) {
                            try {
                                readAndCacheImageFromDisk(imageID);
                                loadedFromDisk = true;
                            } catch (IOException e) {
                                if (OFFLINE_MODE) throw e;
                                BetterLoadingScreen.log.warn("Retrying invalid cached imgur image: " + imageID, e);
                            }
                        }
                        if (!loadedFromDisk) {
                            if (OFFLINE_MODE) return;

                            readAndCacheImageFromStream(
                                    imageID,
                                    new ByteArrayInputStream(client.fetchImage(imageID)),
                                    true);
                        }
                    } catch (IOException e) {
                        BetterLoadingScreen.log.error("Error while loading imgur image", e);
                        return;
                    }

                    synchronized (this) {
                        if (!cancelSetup)
                            textureLocationConsumer.accept(new ResourceLocation(IMGUR_CACHE_DIR, imageID));
                    }
                };

                if (OFFLINE_MODE) {
                    cachedImageIDs.stream().parallel().forEach(imageHandler);
                } else {
                    client.fetchGalleryImageIDs(galleryId, true).stream().parallel().forEach(imageHandler);
                }
            } catch (Exception e) {
                BetterLoadingScreen.log.error("Error while fetching imgur gallery", e);
                throw new CompletionException(e);
            }
        }).thenRunAsync(() -> {
            if (OFFLINE_MODE || cancelSetup) return;

            // Delete cached images that are no longer in the gallery
            try {
                for (String id : cachedImageIDs) {
                    if (cancelSetup) return;
                    Files.deleteIfExists(getCachedImagePath(id));
                }
            } catch (IOException e) {
                BetterLoadingScreen.log.error("Error while deleting unused cached imgur images", e);
            }
        });
    }

    private void loadAnyImageFromDisk(List<String> cachedImageIDs, Consumer<ResourceLocation> textureLocationConsumer) {
        if (cachedImageIDs.isEmpty()) return;

        int start = ThreadLocalRandom.current().nextInt(cachedImageIDs.size());
        for (int i = 0; i < cachedImageIDs.size(); i++) {
            if (cancelSetup) return;
            String imageID = cachedImageIDs.get((start + i) % cachedImageIDs.size());
            try {
                readAndCacheImageFromDisk(imageID);
            } catch (IOException e) {
                BetterLoadingScreen.log.warn("Skipping unreadable cached imgur image: " + imageID, e);
                continue;
            }

            synchronized (this) {
                if (!cancelSetup) textureLocationConsumer.accept(new ResourceLocation(IMGUR_CACHE_DIR, imageID));
            }
            return;
        }
    }

    private void readAndCacheImageFromStream(String imageID, InputStream imageStream, boolean saveToDisk)
            throws IOException {
        BufferedImage image;
        try (InputStream input = imageStream) {
            if (cancelSetup) return;
            image = ImageIO.read(input);
        }
        if (image == null) throw new IOException("Invalid cached or downloaded imgur image: " + imageID);
        if (cancelSetup) return;
        LateInitDynamicTexture texture = new LateInitDynamicTexture(image, image.getWidth(), image.getHeight());
        if (cancelSetup) return;
        if (saveToDisk) {
            try {
                writeImageToCache(imageID, image);
            } catch (IOException e) {
                BetterLoadingScreen.log.warn("Unable to cache imgur image on disk: " + imageID, e);
            }
        }
        synchronized (this) {
            if (cancelSetup) return;
            textureCache.put(imageID, texture);
        }
    }

    private void readAndCacheImageFromDisk(String imageID) throws IOException {
        readAndCacheImageFromStream(
                imageID,
                new BufferedInputStream(Files.newInputStream(getCachedImagePath(imageID)), 1024 * 1024),
                false);
    }

    private void writeImageToCache(String imageID, BufferedImage image) throws IOException {
        try (OutputStream output = new BufferedOutputStream(
                Files.newOutputStream(getCachedImagePath(imageID)),
                1024 * 1024)) {
            ImageIO.write(image, "png", output);
        }
    }

    private static Path getCachedImagePath(String imageID) {
        return Paths.get(IMGUR_CACHE_DIR).resolve(imageID + ".png");
    }

    private List<String> getCachedImageIDs() {
        try (Stream<Path> cacheFolderStream = Files.list(Paths.get(IMGUR_CACHE_DIR))) {
            return cacheFolderStream.map(path -> path.getFileName().toString().replace(".png", ""))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            BetterLoadingScreen.log.error("Error while iterating imgur cache folder", e);
            return new ArrayList<>();
        }
    }
}
