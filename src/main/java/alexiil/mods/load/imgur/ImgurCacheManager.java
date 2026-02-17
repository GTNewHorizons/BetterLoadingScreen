package alexiil.mods.load.imgur;

import java.util.List;
import java.util.stream.Collectors;

import net.minecraftforge.common.config.Configuration;

import alexiil.mods.load.RemoteCacheManager;

public class ImgurCacheManager extends RemoteCacheManager<ImgurClient> {

    private String appClientId;
    private String galleryId;
    private int requestTimeout;

    public ImgurCacheManager() {
        super("bls-imgur-cache", "imgur");
    }

    @Override
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

    @Override
    protected ImgurClient createClient() {
        return new ImgurClient(appClientId, requestTimeout);
    }

    @Override
    protected List<ImageEntry> fetchRemoteImages(ImgurClient client) throws Exception {
        return client.fetchGalleryImageIDs(galleryId, true).stream().map(id -> new ImageEntry(id, id))
                .collect(Collectors.toList());
    }

    @Override
    protected byte[] downloadImage(ImgurClient client, ImageEntry entry) {
        return client.fetchImage(entry.downloadRef);
    }
}
