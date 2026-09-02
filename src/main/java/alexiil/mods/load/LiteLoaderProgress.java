package alexiil.mods.load;

import java.io.IOException;

import com.mumfrey.liteloader.client.gui.startup.LoadingBar;

import alexiil.mods.load.FMLProgressTracker.Stage;

public class LiteLoaderProgress extends LoadingBar {

    private static final int LITE_LOADER_INIT_ORDINAL = Stage.LITE_LOADER_INIT.ordinal();
    private static final float LITE_LOADER_START_PERCENT = LITE_LOADER_INIT_ORDINAL * Stage.PROGRESS_SPAN;

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
        float percent = LITE_LOADER_START_PERCENT + Stage.PROGRESS_SPAN * liteProgress / totalLiteProgress;
        ProgressDisplayer.displayProgress("LiteLoader: " + message, percent);
    }

    @Override
    protected void _setEnabled(boolean arg0) {}

    @Override
    protected void _setMessage(String arg0) {
        message = arg0;
    }
}
