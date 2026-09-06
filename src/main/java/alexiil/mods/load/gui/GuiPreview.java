package alexiil.mods.load.gui;

import javax.swing.SwingUtilities;

import net.minecraft.client.Minecraft;
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
        if (displayer != null) displayer.displayProgressInWorkerThread(debugText, debugPercent);
    }

    @Override
    protected void keyTyped(char chr, int type) {
        if (type == 1) { // Esc
            close();
        }
    }

    public void close() {
        Minecraft client = Minecraft.getMinecraft();
        client.func_152344_a(() -> {
            onGuiClosed();
            if (client.currentScreen == this) client.displayGuiScreen(parent);
        });
    }

    @Override
    public void onGuiClosed() {
        if (displayer != null) {
            MinecraftDisplayer oldDisplayer = displayer;
            displayer = null;
            oldDisplayer.close();
        }
        if (preview != null) {
            FramePreview oldPreview = preview;
            preview = null;
            SwingUtilities.invokeLater(oldPreview::dispose);
        }
    }

    public ImageRender[] getImageData() {
        return displayer.getImageData();
    }

    public void setImageData(ImageRender[] data) {
        Minecraft.getMinecraft().func_152344_a(() -> {
            if (displayer == null) return;
            displayer.close();
            displayer = new MinecraftDisplayer(true);
            displayer.openPreview(data);
        });
    }
}
