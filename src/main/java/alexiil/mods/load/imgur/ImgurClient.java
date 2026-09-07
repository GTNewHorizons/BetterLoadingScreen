package alexiil.mods.load.imgur;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

public class ImgurClient implements AutoCloseable {

    private final CloseableHttpClient client;

    public ImgurClient(String clientId, int requestTimeout) {
        this.client = HttpClients.custom()
                .setDefaultHeaders(Collections.singletonList(new BasicHeader("Authorization", "Client-ID " + clientId)))
                .setDefaultRequestConfig(
                        RequestConfig.custom().setConnectTimeout(requestTimeout)
                                .setConnectionRequestTimeout(requestTimeout).setSocketTimeout(requestTimeout).build())
                .build();
    }

    public List<String> fetchGalleryImageIDs(String galleryId, boolean ignoreAds)
            throws IOException, JsonParseException {
        try (CloseableHttpResponse response = client
                .execute(new HttpGet("https://api.imgur.com/3/album/" + galleryId))) {
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK)
                throw new IOException("Failed to fetch gallery image IDs. Server returned " + response.getStatusLine());

            String json = new String(IOUtils.toByteArray(response.getEntity().getContent()), StandardCharsets.UTF_8);
            GalleryResponse galleryResponse = new GsonBuilder().create().fromJson(json, GalleryResponse.class);

            if (galleryResponse.data == null || galleryResponse.data.images == null)
                throw new IOException("Server returned unexpected json format: " + json);

            return galleryResponse.data.images.stream().filter(image -> !ignoreAds || !image.is_ad)
                    .map(image -> image.id).collect(Collectors.toList());
        }
    }

    public byte[] fetchImage(String imageId) throws IOException {
        // Note that Imgur allows requesting JPG images as PNGs, although the returned image will still be a JPG.
        try (CloseableHttpResponse response = client.execute(new HttpGet("https://i.imgur.com/" + imageId + ".png"))) {
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK)
                throw new IOException("Failed to fetch image. Server returned " + response.getStatusLine());

            return IOUtils.toByteArray(response.getEntity().getContent());
        }
    }

    @Override
    public void close() throws Exception {
        this.client.close();
    }

    private static class GalleryResponse {

        public GalleryData data;

        private static class GalleryData {

            public List<GalleryImage> images;

            private static class GalleryImage {

                public String id;
                public boolean is_ad;
            }
        }
    }
}
