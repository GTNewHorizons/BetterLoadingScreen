package alexiil.mods.load.gui;

import net.minecraft.client.gui.GuiScreen;

import alexiil.mods.load.MinecraftDisplayer;
import alexiil.mods.load.ProgressDisplayer;
import alexiil.mods.load.json.ImageRender;

public class GuiPreview extends GuiScreen {

    private final BaseConfig parent;
    private MinecraftDisplayer displayer;

    public String debugText = "Random Text";
    public float debugPercent = 0.2f;

    private FramePreview preview;

    public GuiPreview(BaseConfig parent) {
        this.parent = parent;
        displayer = new MinecraftDisplayer(true);
        displayer.open(ProgressDisplayer.cfg);

        preview = new FramePreview(this);
        preview.setVisible(true);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        displayer.displayProgress(debugText, debugPercent, null, Float.NaN);
    }

    @Override
    protected void keyTyped(char chr, int type) {
        if (type == 1) { // Esc
            close();
        }
    }

    public void close() {
        mc.displayGuiScreen(parent);
        preview.dispose();
        preview = null;
    }

    public ImageRender[] getImageData() {
        return displayer.getImageData();
    }

    public void setImageData(ImageRender[] data) {
        displayer = new MinecraftDisplayer(true);
        displayer.openPreview(data);
    }
}
