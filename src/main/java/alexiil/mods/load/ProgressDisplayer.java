package alexiil.mods.load;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.config.Configuration;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;

import cpw.mods.fml.client.FMLFileResourcePack;
import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ProgressDisplayer {

    private static boolean hasTurnedSplashOff = false;
    private static boolean forgeSplashWasTrue = false;

    public interface IDisplayer {

        void open(Configuration cfg);

        void displayProgress(String text, float percent);

        void close();
    }

    public static class FrameDisplayer implements IDisplayer {

        private LoadingFrame frame = null;

        @Override
        public void open(Configuration cfg) {
            frame = LoadingFrame.openWindow();
            if (frame != null) {
                frame.setMessage("Minecraft Forge Starting");
                frame.setProgress(0);
            }
        }

        @Override
        public void displayProgress(String text, float percent) {
            if (frame == null) return;
            frame.setMessage(text);
            frame.setProgress(percent * 100F);
            frame.repaint();
        }

        @Override
        public void close() {
            if (frame != null) frame.dispose();
        }
    }

    public static class LoggingDisplayer implements IDisplayer {

        @Override
        public void open(Configuration cfg) {}

        @Override
        public void displayProgress(String text, float percent) {
            BetterLoadingScreen.log.info(text + " (" + (int) (percent * 100) + "%)");
        }

        @Override
        public void close() {}
    }

    private static IDisplayer displayer;
    private static int clientState = -1;
    public static Configuration cfg;
    public static boolean playSound;
    public static File coreModLocation;
    public static ModContainer modContainer;

    private static boolean hasInitRL = false;

    public static boolean isClient() {
        if (clientState != -1) return clientState == 1;
        StackTraceElement[] steArr = Thread.currentThread().getStackTrace();
        for (StackTraceElement ste : steArr) {
            if (ste.getClassName().startsWith("cpw.mods.fml.relauncher.ServerLaunchWrapper")) {
                clientState = 0;
                return false;
            }
        }
        clientState = 1;
        return true;
    }

    private static void loadResourceLoader() {
        try {
            Class<?> resLoaderClass = Class.forName("lumien.resourceloader.ResourceLoader");
            Object instance = resLoaderClass.newInstance();
            resLoaderClass.getField("INSTANCE").set(null, instance);
            Method m = resLoaderClass.getMethod("preInit", FMLPreInitializationEvent.class);
            m.invoke(instance, new Object[] { null });
            BetterLoadingScreen.log.debug("Resource loader loaded early successfully :)");
        } catch (ClassNotFoundException ex) {
            BetterLoadingScreen.log.warn("Resource loader not loaded, not initialising early");
        } catch (Throwable t) {
            BetterLoadingScreen.log.error("Resource Loader Compat FAILED!");
            t.printStackTrace();
        }
    }

    public static void start(File coremodLocation) {
        LoadingFrame.setSystemLAF();
        coreModLocation = coremodLocation;
        if (coreModLocation == null) coreModLocation = new File("./../bin/");
        // Assume this is a dev environment, and that the build dir is in bin, and the test dir has the same parent as
        // the bin dir...
        ModMetadata md = new ModMetadata();
        md.name = Lib.Mod.NAME;
        md.modId = Lib.Mod.ID;
        modContainer = new DummyModContainer(md) {

            @Override
            public Class<?> getCustomResourcePackClass() {
                return FMLFileResourcePack.class;
            }

            @Override
            public File getSource() {
                return coreModLocation;
            }

            @Override
            public String getModId() {
                return Lib.Mod.ID;
            }
        };

        // File fileOld = new File("./config/betterloadingscreen.cfg");
        // File fileNew = new File("./config/BetterLoadingScreen/config.cfg");
        File fileOld = new File("./config/Betterloadingscreen/betterloadingscreen.cfg");

        /*
         * if (fileOld.exists()) cfg = new Configuration(fileOld); else cfg = new Configuration(fileNew);
         */
        String n = System.lineSeparator();
        cfg = new Configuration(fileOld);

        boolean useMinecraft = isClient();

        if (useMinecraft) {
            String comment = "Whether or not to use minecraft's display to show the progress. This looks better, but there is a possibility of not being ";
            comment += "compatible, so if you do have any strange crash reports or compatibility issues, try setting this to false"
                    + n
                    + "Note: IIRC, setting this to false makes the screen black";
            useMinecraft = cfg.getBoolean("useMinecraft", "general", true, comment);
        }

        playSound = cfg
                .getBoolean("playSound", "general", true, "Play a sound after minecraft has finished starting up");

        if (useMinecraft) displayer = new MinecraftDisplayerWrapper();
        else if (!GraphicsEnvironment.isHeadless()) displayer = new FrameDisplayer();
        else displayer = new LoggingDisplayer();
        displayer.open(cfg);
        cfg.save();
    }

    public static boolean setForgeSplashEnabled(boolean enabled) throws IOException {
        boolean hasTurnedOff = false;
        File configFile = new File(Minecraft.getMinecraft().mcDataDir, "config/splash.properties");
        FileReader r = null;
        Properties config = new Properties();
        try {
            r = new FileReader(configFile);
            config.load(r);
        } catch (IOException e) {
            BetterLoadingScreen.log.info("Forge splash screen settings not found, will create a dummy one");
        } finally {
            IOUtils.closeQuietly(r);
        }
        config.setProperty("enabled", Boolean.toString(enabled));
        FileWriter w = null;
        try {
            w = new FileWriter(configFile);
            config.store(w, "Splash screen properties");
            hasTurnedOff = true;
            BetterLoadingScreen.log
                    .info("Turned Forge splash screen " + (enabled ? "on" : "off") + " in splash.properties");
        } catch (IOException e) {
            BetterLoadingScreen.log.log(
                    Level.ERROR,
                    "Could not turn Forge splash screen " + (enabled ? "on" : "off") + " in splash.properties",
                    e);
        } finally {
            IOUtils.closeQuietly(w);
        }
        return hasTurnedOff;
    }

    public static void displayProgress(String text, float percent) throws IOException {
        if (!hasTurnedSplashOff) {
            hasTurnedSplashOff = true;
            if (setForgeSplashEnabled(false)) {
                forgeSplashWasTrue = true;
            }
        }
        if (!hasInitRL) {
            loadResourceLoader();
            overrideForgeSplashProgress();
            hasInitRL = true;
        }
        try {
            displayer.displayProgress(text, percent);
        } catch (Throwable t) {}
    }

    public static void close() throws IOException {
        if (displayer == null) return;
        displayer.close();
        displayer = null;
        if (isClient() && playSound) {
            final Thread dingThread = new Thread() {

                @Override
                @SideOnly(Side.CLIENT)
                public void run() {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {}
                    MinecraftDisplayerWrapper.playFinishedSound();
                }
            };
            dingThread.setDaemon(true);
            dingThread.start();
        }
        if (forgeSplashWasTrue) {
            setForgeSplashEnabled(true);
        }
    }

    private static void overrideForgeSplashProgress() {
        Class<?> cl = null;
        Field fi = null;
        try {
            cl = Class.forName("cpw.mods.fml.client.SplashProgress");
            fi = cl.getDeclaredField("enabled");
            fi.setAccessible(true);
            fi.set(null, false);
            // Set this just to make forge's screen exit ASAP.
            fi = cl.getDeclaredField("done");
            fi.setAccessible(true);
            fi.set(null, true);
        } catch (Throwable t) {
            BetterLoadingScreen.log.error("Could not override forge's splash screen for some reason...");
            BetterLoadingScreen.log.error("class = " + cl);
            BetterLoadingScreen.log.error("field = " + fi);
            t.printStackTrace();
        }
    }

    public static void minecraftDisplayFirstProgress() throws IOException {
        displayProgress(
                Translation.translate("betterloadingscreen.state.minecraft_init", "Minecraft Initializing"),
                0F);
    }
}
