package alexiil.mods.load;

import net.minecraftforge.common.config.Configuration;

import alexiil.mods.load.ProgressDisplayer.IDisplayer;

public class MinecraftDisplayerWrapper implements IDisplayer {

    private MinecraftDisplayer mcDisplayer;
    private Configuration cfg;
    private boolean opening;

    @Override
    public void open(Configuration cfg) {
        this.cfg = cfg;
    }

    @Override
    public void displayProgress(String text, float percent, String subText, float subPercent) {
        if (mcDisplayer != null) {
            mcDisplayer.displayProgress(text, percent, subText, subPercent);
            return;
        }

        // newDisplayer.open() can report FML progress while refreshing resources, which re-enters this method
        if (opening) return;

        opening = true;
        try {
            MinecraftDisplayer newDisplayer = new MinecraftDisplayer();
            newDisplayer.open(cfg);
            mcDisplayer = newDisplayer;
        } catch (Throwable t) {
            BetterLoadingScreen.log.error("Failed to load Minecraft Displayer!", t);
        } finally {
            opening = false;
        }
        cfg.save();
    }

    @Override
    public void close() {
        if (mcDisplayer != null) mcDisplayer.close();
    }

    public static void playFinishedSound() {
        MinecraftDisplayer.playFinishedSound();
    }
}
