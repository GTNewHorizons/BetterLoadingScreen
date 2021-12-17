package alexiil.mods.load.json;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import alexiil.mods.load.BetterLoadingScreen;
import com.google.gson.JsonArray;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lwjgl.Sys;

public class ImgurTest {
    private static OkHttpClient httpClient;
    //private OkHttpClient httpClient;
    private static final String USER_AGENT = "BetterLoadingScreenMod";
    //private static final String GET_URL = "https://api.imgur.com/3/album/Ks0TrYE";
    //private static final String GET_URL = "https://imgur.com/gallery/Ks0TrYE";
    private static final String CLIENT_ID = "55141d737288505";

    public static String imgurUrlToApiUrl(String url) {
        if(url.indexOf("https") != -1){
            return "https://api.imgur.com/3/album/" + url.substring(26);
        } else {
            return "https://api.imgur.com/3/album/" + url.substring(25);
        }
    }

    public static java.util.List<java.lang.String> fetchImgurGallery(String url) throws IOException {
        url = imgurUrlToApiUrl(url);
        BetterLoadingScreen.log.trace("sendget func");
        final List<String> images = new ArrayList<>();
        httpClient = new OkHttpClient.Builder().build();
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization","Client-ID " + CLIENT_ID)
                .header("User-Agent",USER_AGENT)
                .build();
        httpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                BetterLoadingScreen.log.error("An error has occurred " + e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException
            {
                BetterLoadingScreen.log.trace("got a response, askip");
                try
                {
                    JSONObject data = new JSONObject(response.body().string());
                    BetterLoadingScreen.log.trace(data.get("data").toString());
                    JSONObject data2 = new JSONObject(data.get("data").toString());
                    BetterLoadingScreen.log.trace(data2.get("images").toString());

                    JSONArray image_objs = data2.getJSONArray("images");

                    for (int i = 0; i < image_objs.length(); i++) {
                        JSONObject image = image_objs.getJSONObject(i);
                        if (!image.getBoolean("is_ad")) {
                            images.add(image.getString("link"));
                        }
                    }

                    BetterLoadingScreen.log.trace("Image list:\n" + images);

                    //JSONArray items = data.getJSONArray("data");

                    //JSONObject jsonObject = new JSONObject(data);

                    //BetterLoadingScreen.log.trace(data.keys().toString());

                    //Iterator<String> keys = data.get("data").keys();

                    /*while(keys.hasNext()) {
                        String key = keys.next();
                        if (data.get(key) instanceof JSONObject) {
                            BetterLoadingScreen.log.trace(((JSONObject) data.get(key)).toString(4));
                        }
                    }*/
                    //final List<Photo> photos = new ArrayList<>();

                    /*for (int i = 0; i < items.length(); i++)
                    {
                        JSONObject item = items.getJSONObject(i);
                        BetterLoadingScreen.log.trace(item.toString(4));
                    }*/

                } catch (JSONException e)
                {
                    e.printStackTrace();
                }
            }
        });
        return images;
    }
}
