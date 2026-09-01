package alexiil.mods.load;

import java.io.IOException;

import com.mumfrey.liteloader.client.gui.startup.LoadingBar;

public class LiteLoaderProgress extends LoadingBar {

    private static final int LITE_LOADER_INIT_ORDINAL = FMLProgressTracker.Stage.LITE_LOADER_INIT.ordinal();
    private static final float STAGE_SIZE = 1F / (FMLProgressTracker.Stage.VALUES.length - 1);
    private static final float LITE_LOADER_START_PERCENT = LITE_LOADER_INIT_ORDINAL * STAGE_SIZE;

    private String message = "";
    private int totalLiteProgress = 0;
    private int liteProgress = 0;

    @Override
    protected void _dispose() {}

    @Override
    protected void _incLiteLoaderProgress() {
        _incLiteLoaderProgress(message);
    }

    @Override
    protected void _incLiteLoaderProgress(String arg0) {
        message = arg0;
        liteProgress++;
        try {
            render();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    protected void _incTotalLiteLoaderProgress(int arg0) {
        totalLiteProgress += arg0;
        try {
            render();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void render() throws IOException {
        float percent = LITE_LOADER_START_PERCENT + STAGE_SIZE * liteProgress / totalLiteProgress;
        ProgressDisplayer.displayProgress("LiteLoader: " + message, percent);
    }

    @Override
    protected void _setEnabled(boolean arg0) {}

    @Override
    protected void _setMessage(String arg0) {
        message = arg0;
    }
}
