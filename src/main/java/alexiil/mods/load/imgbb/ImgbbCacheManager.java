package alexiil.mods.load.imgbb;

import java.util.List;
import java.util.stream.Collectors;

import net.minecraftforge.common.config.Configuration;

import alexiil.mods.load.RemoteCacheManager;

public class ImgbbCacheManager extends RemoteCacheManager<ImgbbClient> {

    private String albumId;
    private int requestTimeout;

    public ImgbbCacheManager() {
        super("bls-imgbb-cache", "imgbb");
    }

    @Override
    public void loadConfig(Configuration config) {
        albumId = config.getString("imgbbAlbumId", "imgbb", "", "ID of the imgbb album. For example: X7wpP4");
        requestTimeout = config.getInt(
                "imgbbRequestTimeout",
                "imgbb",
                5000,
                100,
                Integer.MAX_VALUE,
                "Request timeout (ms) for imgbb requests");
    }

    @Override
    protected ImgbbClient createClient() {
        return new ImgbbClient(requestTimeout);
    }

    @Override
    protected List<ImageEntry> fetchRemoteImages(ImgbbClient client) throws Exception {
        return client.fetchAlbumImageURLs(albumId).stream().map(url -> new ImageEntry(urlToImageID(url), url))
                .collect(Collectors.toList());
    }

    @Override
    protected byte[] downloadImage(ImgbbClient client, ImageEntry entry) {
        return client.fetchImage(entry.downloadRef);
    }

    /**
     * Extracts a stable image ID from an imgbb direct URL. Uses the URL path code as the ID. For example,
     * {@code https://i.ibb.co/j9rBbcHy/02.png} becomes {@code j9rBbcHy_02}.
     */
    static String urlToImageID(String url) {
        // URL format: https://i.ibb.co/{code}/{filename}.{ext}
        String path = url.replace("https://i.ibb.co/", "");
        // path is now "{code}/{filename}.{ext}"
        int lastDot = path.lastIndexOf('.');
        if (lastDot != -1) {
            path = path.substring(0, lastDot);
        }
        return path.replace('/', '_');
    }
}
