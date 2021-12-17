package alexiil.mods.load.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import alexiil.mods.load.Lib;
import alexiil.mods.load.ProgressDisplayer;
import alexiil.mods.load.Translation;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;

public class ActualConfig extends GuiConfig {
    public ActualConfig(GuiScreen parent) {
        super(parent, getConfigElements(), Lib.Mod.ID, false, true, Translation.translate("alexiillib.config.title", "Main Configuration File"));
    }

    @SuppressWarnings("rawtypes")
    private static List<IConfigElement> getConfigElements() {
        List<IConfigElement> elements = new ArrayList<IConfigElement>();
        Configuration cfg = ProgressDisplayer.cfg;
        for (String name : cfg.getCategoryNames()) {
            ConfigCategory cat = cfg.getCategory(name);
            if (!cat.isChild())
                elements.add(new ConfigElement(cfg.getCategory(name)));
        }
        return elements;
    }
}
