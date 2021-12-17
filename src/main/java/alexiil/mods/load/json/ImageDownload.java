package alexiil.mods.load.json;
import alexiil.mods.load.BetterLoadingScreen;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.CountDownLatch;

public class ImageDownload {
    public static String dlImage(String direct_url, String name) {
        BetterLoadingScreen.log.trace("Entered dlImage function, url is: " + direct_url);
        BufferedImage image =null;
        try{
            URL url =new URL(direct_url);
            // read the url
            image = ImageIO.read(url);

            BetterLoadingScreen.log.trace("ending of file: "+direct_url.substring(direct_url.length()-3));
            if (direct_url.substring(direct_url.length()-3).equals("jpg")) {
                ImageIO.write(image, "jpg",new File("/cls_cache/"+ name +".jpg"));
                return "/cls_cache/"+ name +".jpg";
            } else {
                ImageIO.write(image, "png",new File("/cls_cache/"+ name +".png"));
                return "/cls_cache/"+ name +".png";
            }
        }catch(IOException e){
            e.printStackTrace();
        }
        return "";
    }
}

