package alexiil.mods.load.json;

import alexiil.mods.load.BetterLoadingScreen;
import alexiil.mods.load.MinecraftDisplayer;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class DlAllImages implements Runnable {

    public DlAllImages (CountDownLatch countDownLatch) {
        this.countDownLatch = countDownLatch;
    }

    private CountDownLatch countDownLatch;
    @Override
    public void run() {
        BetterLoadingScreen.log.trace("hmmmmmm");
        List<String> images = null;
        try {
            BetterLoadingScreen.log.debug("Getting imgur gallery");
            images = ImgurTest.fetchImgurGallery(MinecraftDisplayer.imgurGalleryLink);
        } catch (IOException e) {
            BetterLoadingScreen.log.error("Couldn't get imgur gallery");
            e.printStackTrace();
        }
        BetterLoadingScreen.log.debug("Got the gallery");
        String[] imageUrls = images.toArray(new String[0]);
        BetterLoadingScreen.log.trace("got here, imageUrls: " + imageUrls.toString());
        BetterLoadingScreen.log.trace("images.length: " + String.valueOf(images.size()));
        List<String> imagePaths = null;
        for (int i = 0; i < imageUrls.length; i++) {
            BetterLoadingScreen.log.trace("Downloading " + i + "th image");
            imagePaths.add(ImageDownload.dlImage(imageUrls[i], String.valueOf(i)));
        }
        MinecraftDisplayer.randomBackgroundArray = imagePaths.toArray(new String[0]);
        BetterLoadingScreen.log.trace("bg_array is: "+MinecraftDisplayer.randomBackgroundArray.toString());

        countDownLatch.countDown();
    }
}
