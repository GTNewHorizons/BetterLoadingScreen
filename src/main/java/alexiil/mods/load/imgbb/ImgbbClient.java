package alexiil.mods.load.imgbb;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class ImgbbClient implements AutoCloseable {

    private static final Pattern IMAGE_URL_PATTERN = Pattern
            .compile("image-container --media\"><img src=\"(https://i\\.ibb\\.co/[^\"]+)\"");

    private final CloseableHttpClient client;

    public ImgbbClient(int requestTimeout) {
        this.client = HttpClients.custom().setDefaultHeaders(Collections.emptyList())
                .setDefaultRequestConfig(
                        RequestConfig.custom().setConnectTimeout(requestTimeout)
                                .setConnectionRequestTimeout(requestTimeout).setSocketTimeout(requestTimeout).build())
                .build();
    }

    public List<String> fetchAlbumImageURLs(String albumId) throws IOException {
        try (CloseableHttpResponse response = client.execute(new HttpGet("https://ibb.co/album/" + albumId))) {
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK)
                throw new IOException("Failed to fetch imgbb album. Server returned " + response.getStatusLine());

            String html = new String(IOUtils.toByteArray(response.getEntity().getContent()), StandardCharsets.UTF_8);

            List<String> urls = new ArrayList<>();
            Matcher matcher = IMAGE_URL_PATTERN.matcher(html);
            while (matcher.find()) {
                urls.add(matcher.group(1));
            }

            if (urls.isEmpty()) throw new IOException("No images found in imgbb album: " + albumId);

            return urls;
        }
    }

    public byte[] fetchImage(String imageUrl) {
        try (CloseableHttpResponse response = client.execute(new HttpGet(imageUrl))) {
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK)
                throw new IOException("Failed to fetch image. Server returned " + response.getStatusLine());

            return IOUtils.toByteArray(response.getEntity().getContent());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws Exception {
        this.client.close();
    }
}
