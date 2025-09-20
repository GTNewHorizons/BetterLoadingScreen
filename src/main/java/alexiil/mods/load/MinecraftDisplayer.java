package alexiil.mods.load;

import static org.lwjgl.opengl.GL11.*;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundEventAccessorComposite;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.LanguageManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;

import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.SharedDrawable;

import alexiil.mods.load.ProgressDisplayer.IDisplayer;
import alexiil.mods.load.imgur.ImgurCacheManager;
import alexiil.mods.load.json.Area;
import alexiil.mods.load.json.EPosition;
import alexiil.mods.load.json.EType;
import alexiil.mods.load.json.ImageRender;
import cpw.mods.fml.client.FMLFileResourcePack;
import cpw.mods.fml.client.FMLFolderResourcePack;
import cpw.mods.fml.client.SplashProgress;

public class MinecraftDisplayer implements IDisplayer {

    private static String sound;
    private static String defaultSound = "betterloadingscreen:rhapsodia_orb";
    private static String fontTexture;
    private static String defaultFontTexture = "textures/font/ascii.png";
    private final boolean preview;
    private ImageRender[] images;
    private TextureManager textureManager = null;
    private Map<String, FontRenderer> fontRenderers = new HashMap<String, FontRenderer>();
    private FontRenderer fontRenderer = null;
    private ScaledResolution resolution = null;
    private Minecraft mc = null;
    private boolean callAgain = false;
    private IResourcePack myPack;
    private float clearRed = 1, clearGreen = 1, clearBlue = 1;
    private boolean hasSaidNice = false;
    public static float lastPercent = 0;
    private List<String> alreadyUsedBGs = new ArrayList<>();
    private List<String> alreadyUsedTooltips = new ArrayList<>();
    private String GTprogress = "betterloadingscreen:textures/GTMaterialsprogressBars.png";
    private String progress = "betterloadingscreen:textures/mainProgressBar.png";
    private String GTprogressAnimated = "betterloadingscreen:textures/GTMaterialsprogressBars.png";
    private String progressAnimated = "betterloadingscreen:textures/mainProgressBar.png";
    private String title = "betterloadingscreen:textures/transparent.png";
    private String background = "betterloadingscreen:textures/backgrounds/01.png";
    // Coordinate format: {texture x, y, w, h, on-screen x, y, w, h}
    private int[] titlePos = new int[] { 0, 0, 256, 256, 0, 50, 187, 145 };
    /*
     * private int[] GTprogressPos = new int[] {0, 0, 172, 12, 0, -83, 172, 6}; private int[] GTprogressPosAnimated =
     * new int[] {0, 12, 172, 12, 0, -83, 172, 6};
     */
    private int[] GTprogressPos = new int[] { 0, 0, 194, 24, 0, -83, 188, 12 };
    private int[] GTprogressPosAnimated = new int[] { 0, 24, 194, 24, 0, -83, 188, 12 };
    private int[] progressPos = new int[] { 0, 0, 194, 24, 0, -50, 194, 16 };
    private int[] progressPosAnimated = new int[] { 0, 24, 194, 24, 0, -50, 194, 16 };
    private int[] memoryPos = new int[] { 0, 0, 194, 24, 0, 48, 194, 16 };
    private int[] memoryPosAnimated = new int[] { 0, 24, 194, 24, 0, 48, 194, 16 };
    private int[] progressTextPos = new int[] { 0, -30 };
    private int[] progressPercentagePos = new int[] { 0, -40 };
    private int[] GTprogressTextPos = new int[] { 0, -65 };
    private int[] GTprogressPercentagePos = new int[] { 0, -75 };
    private int[] tipsTextPos = new int[] { 0, 5 };
    private String baseTipsTextPos = "BOTTOM_CENTER";
    private boolean tipsEnabled = true;
    private String[] randomTips;
    // private String[] randomTips = new String[] {"Got a question? Join our Discord server","Don't give ideas to
    // 0lafe","Don't feed your machines after midnight","Make sure you have installed a backup mod","Material tiers play
    // a role when breaking pipes","If a machine catches fire, it can explode","Adding water to an empty but hot Boiler
    // will cause an explosion","Avoid eldritch obelisks","You can bind the quests menu to a key, instead of using the
    // book","Pam's gardens can be picked up with right-click","Placing a garden makes it spread","Water garden can grow
    // on land","Battlegear slots are convenient for holding weapons","Taking lava without gloves hurts!","Watch out,
    // food loses saturation","Loot Games give helpful rewards","Using too many translocators can cause TPS lag","Be
    // sure to check out what you can do with mouse tweaks","Protect your machines from rain","Build multiblocks within
    // the same chunk","You will lose your first piece of aluminium dust in the EBF","Shift-right click with a wrench
    // makes a fluid pipe input-only","The bending machine makes plates more efficiently","Some multiblocks can share
    // walls","You can not use the front side of machines","Disable a machine with a soft mallet if it can not finish a
    // recipe","Forestry worktables are a must!","Try the midnight theme for the quests menu","Try the realistic sky
    // resourcepack","Literally flint and steel","Tinker's tools can levelup","Farm Glowflowers for glowstone","Making
    // steel armour? Check out the composite chestplate","Adventurer's backpack? Did you mean integrated crafting grid,
    // bed and fluid storage?","Beware of cable power loss","Machines that get a higher voltage than they can handle
    // explode","Loss on uninsulated cables is twice as big as on insulated ones","Machines require electricity based on
    // the recipe that's being run, not the tier of the machine or anything else","Machines have an internal buffer and
    // the machine draws power from this buffer, not directly from a generator","Tinker's faucets can pour fluids and
    // also gasses into containers","Beware of pollution!","Found a bug? Report it on GitHub","Tinker's smeltery does
    // not double ores","Be sure to check out the wiki","Perditio and vanadiumsteel picks and hammers are really
    // fast","Look for ore chunks","Nerfs incoming!","You can plant oreberries on cropsticks","IC2 Crops can receive
    // bonus environmental statistics based on biome","Weeds spread to empty crop sticks and destroy other crops"};
    private String tipsColor = "ffffff";
    private boolean tipsTextShadow = true;
    private int tipsChangeFrequency = 18;
    private String tip = "";
    private static boolean useCustomTips = false;
    private static String customTipFilename = "en_US";
    private boolean textShadow = true;
    private String textColor = "ffffff";
    private boolean randomBackgrounds = true;
    public static String[] randomBackgroundArray = new String[] { "betterloadingscreen:textures/backgrounds/01.png",
            "betterloadingscreen:textures/backgrounds/02.png" };
    private boolean blendingEnabled = true;
    private int changeFrequency = 40;
    private float blendTimeMillis = 2000;
    private boolean shouldGLClear = false;
    private boolean salt = false;
    private String loadingBarsColor = "fdf900";
    private float[] lbRGB = new float[] { 1, 1, 0 };
    private float loadingBarsAlpha = 0.5F;
    private boolean useImgur = false;

    private boolean saltBGhasBeenRendered = false;

    public static boolean isNice = false;
    public static boolean isRegisteringGTmaterials = false;
    public static boolean isReplacingVanillaMaterials = false;
    public static boolean isRegisteringBartWorks = false;
    public static volatile boolean blending = false;
    public static volatile boolean blendingJustSet = false;
    public static volatile float blendAlpha = 1F;
    public static volatile long blendStartMillis = 0;
    private static String newBlendImage = "none";
    private static int nonStaticElementsToGo;

    private ImgurCacheManager imgurCacheManager = null;

    private ScheduledExecutorService backgroundExec = null;
    private boolean scheduledTipExecSet = false;

    private ScheduledExecutorService tipExec = null;
    private boolean scheduledBackgroundExecSet = false;

    private Thread splashRenderThread = null;
    private boolean splashRenderKillSwitch = false;
    /**
     * During the load phase, the main thread still needs to access OpenGL to load textures, etc. To achieve this, the
     * splash render thread takes over the main context, and the main thread is assigned this shared context. A context
     * can only be active in one thread at a time, hence this solution (inspired by FML's SplashProgress implementation)
     */
    private SharedDrawable loadingDrawable = null;

    private String currentText = "";
    private float currentPercent = 0;

    private boolean experimental = false;

    public static float getLastPercent() {
        return lastPercent;
    }

    public static void playFinishedSound() {
        SoundHandler soundHandler = Minecraft.getMinecraft().getSoundHandler();
        ResourceLocation location = new ResourceLocation(sound);
        SoundEventAccessorComposite snd = soundHandler.getSound(location);
        if (snd == null) {
            BetterLoadingScreen.log.warn("The sound given (" + sound + ") did not give a valid sound!");
            location = new ResourceLocation(defaultSound);
            snd = soundHandler.getSound(location);
        }
        if (snd == null) {
            BetterLoadingScreen.log.warn("Default sound did not give a valid sound!");
            return;
        }
        ISound sound = PositionedSoundRecord.func_147673_a(location);
        soundHandler.playSound(sound);
    }

    public MinecraftDisplayer() {
        this(false);
    }

    public MinecraftDisplayer(boolean preview) {
        this.preview = preview;
    }

    @SuppressWarnings("unchecked")
    private List<IResourcePack> getOnlyList() {
        Field[] flds = mc.getClass().getDeclaredFields();
        for (Field f : flds) {
            if (f.getType().equals(List.class) && !Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                try {
                    return (List<IResourcePack>) f.get(mc);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public void openPreview(ImageRender[] renders) {
        mc = Minecraft.getMinecraft();
        images = renders;
    }

    public static boolean isNumeric(String strNum) {
        if (strNum == null) {
            return false;
        }
        try {
            double d = Double.parseDouble(strNum);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    public int[] stringToIntArray(String str) {
        str = str.replaceAll("\\s+", "");
        String intBuffer = "";
        List<Integer> numbers = new ArrayList<Integer>();
        for (int i = 0; i < str.length(); i++) {
            if (isNumeric(String.valueOf(str.charAt(i))) || String.valueOf(str.charAt(i)).equals("-")) {
                intBuffer += String.valueOf(str.charAt(i));
            }
            if (String.valueOf(str.charAt(i)).equals(",") || String.valueOf(str.charAt(i)).equals("]")) {
                numbers.add(Integer.parseInt(intBuffer));
                intBuffer = "";
            }
        }
        int[] res = new int[numbers.size()];
        for (int i = 0; i < numbers.size(); i++) {
            res[i] = numbers.get(i);
        }
        return res;
    }

    public String intArrayToString(int[] array) {
        String res = "[";
        for (int i = 0; i < array.length; i++) {
            res += String.valueOf(array[i]);
            if (i != array.length - 1) {
                res += ", ";
            } else {
                res += "]";
            }
        }
        return res;
    }

    public String parseBackgroundArraytoCFGList(String[] backgrounds) {
        String res = "{"; // +System.lineSeparator();
        for (int i = 0; i < backgrounds.length; i++) {
            res += "" + backgrounds[i];
            if (i < backgrounds.length - 1) {
                res += ", "; // +System.lineSeparator();
            }
        }
        res += "}";
        return res;
    }

    public String[] parseBackgroundCFGListToArray(String backgrounds) {
        String[] res = backgrounds.split(",");
        for (int i = 0; i < res.length; i++) {
            if (String.valueOf(res[i].charAt(0)).equals(" ") || String.valueOf(res[i].charAt(0)).equals("{")) {
                res[i] = res[i].substring(1);
            }
            if (String.valueOf(res[i].charAt(res[i].length() - 1)).equals(" ")
                    || String.valueOf(res[i].charAt(res[i].length() - 1)).equals("}")) {
                res[i] = res[i].substring(0, res[i].length() - 1);
            }
        }
        return res;
    }

    public String randomBackground(String currentBG) {
        if (randomBackgroundArray.length == 1) {
            return randomBackgroundArray[0];
        }
        // BetterLoadingScreen.log.trace("currentBG is: "+currentBG);
        Random rand = new Random();
        String res = randomBackgroundArray[rand.nextInt(randomBackgroundArray.length)];
        // BetterLoadingScreen.log.trace("New res is: "+res);
        // BetterLoadingScreen.log.trace("Does alreadyUsedBGs contain res?:
        // "+String.valueOf(alreadyUsedBGs.contains(res)));
        if (randomBackgroundArray.length == alreadyUsedBGs.size()) {
            alreadyUsedBGs.clear();
        }
        while (res.equals(currentBG) || alreadyUsedBGs.contains(res)) {
            res = randomBackgroundArray[rand.nextInt(randomBackgroundArray.length)];
            // BetterLoadingScreen.log.trace("Rerolled res is: "+res);
        }
        alreadyUsedBGs.add(res);
        // BetterLoadingScreen.log.trace("res is: "+res);
        return res;
    }

    public String randomTooltip(String currentTooltip) {
        if (randomTips.length == 1) {
            return randomTips[0];
        }
        // BetterLoadingScreen.log.trace("currentTooltip is: " + currentTooltip);
        Random rand = new Random();
        String res = randomTips[rand.nextInt(randomTips.length)];
        // BetterLoadingScreen.log.trace("New res (tooltip) is: "+res);
        // BetterLoadingScreen.log.trace("Does alreadyUsedTooltips contain res?:
        // "+String.valueOf(alreadyUsedTooltips.contains(res)));
        if (randomTips.length == alreadyUsedTooltips.size()) {
            alreadyUsedTooltips.clear();
        }
        while (res.equals(currentTooltip) || alreadyUsedTooltips.contains(res)) {
            res = randomTips[rand.nextInt(randomTips.length)];
            // BetterLoadingScreen.log.debug("Rerolled res (tooltip) is: "+res);
        }
        alreadyUsedTooltips.add(res);
        // BetterLoadingScreen.log.debug("res is: "+res);
        return res;
    }

    public static String[] readTipsFile(String file) throws IOException {
        BufferedReader reader = null;
        List<String> lines = new ArrayList<>();
        try {
            reader = new BufferedReader((new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))); // new
            // BufferedReader(new
            // FileReader(file));
            StringBuffer inputBuffer = new StringBuffer();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.charAt(0) != '#') {
                    lines.add(line);
                }
                inputBuffer.append(line);
                inputBuffer.append('\n');
            }
            if (lines.size() == 0) {
                lines.add("No tips!");
            }
            reader.close();

            FileOutputStream fileOut = new FileOutputStream(file);
            PrintStream stream = new PrintStream(fileOut, true, "UTF-8");
            fileOut.write(inputBuffer.toString().getBytes(StandardCharsets.UTF_8));
            fileOut.close();
        } catch (FileNotFoundException e) {
            BetterLoadingScreen.log.error("Error while opening tips file");
            return new String[] { "Failed to load tips! If you didn't do anything, complain on the GTNH Discord" };
        }
        return lines.toArray(new String[0]);
    }

    public static void placeTipsFile() throws IOException {
        String locale = "en_US";
        if (!useCustomTips) {
            BetterLoadingScreen.log.info("Not using custom tooltips");
            locale = Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode();
            // log.info("Using locale " + locale + "(0)");
            if (locale.length() > 5) {
                locale = locale.substring(0, 5);
            }
        } else {
            locale = customTipFilename;
            BetterLoadingScreen.log.info("Using custom tooltips, name: " + locale);
        }
        // BetterLoadingScreen.log.trace("getting resource");
        // InputStream fileContents = Minecraft.getMinecraft().getResourceManager().getResource(new
        // ResourceLocation("betterloadingscreen:tips/tips.txt")).getInputStream();
        InputStream fileContents = null;
        try {
            fileContents = Minecraft.getMinecraft().getResourceManager()
                    .getResource(new ResourceLocation("betterloadingscreen:tips/" + locale + ".txt")).getInputStream();
        } catch (Exception e) {
            fileContents = Minecraft.getMinecraft().getResourceManager()
                    .getResource(new ResourceLocation("betterloadingscreen:tips/en_US.txt")).getInputStream();
            locale = "en_US";
            BetterLoadingScreen.log.info("Language not found");
        }
        byte[] buffer = new byte[fileContents.available()];
        fileContents.read(buffer);
        // BetterLoadingScreen.log.trace("got resource?");
        File dir = new File("./config/Betterloadingscreen/tips");
        if (!dir.exists()) {
            BetterLoadingScreen.log.warn("tips dir does not exist");
            dir.mkdirs();
        } else {
            BetterLoadingScreen.log.debug("tips dir exists");
        }
        BetterLoadingScreen.log.debug("Current locale: " + locale);
        File dest = new File("./config/Betterloadingscreen/tips/" + locale + ".txt");
        BetterLoadingScreen.log.debug("dest set");
        OutputStream outStream = new FileOutputStream(dest);
        // BetterLoadingScreen.log.trace("outputstream set");
        outStream.write(buffer);
        // BetterLoadingScreen.log.trace("buffer write");
    }

    public void handleTips() {
        String locale = "en_US";
        if (!useCustomTips) {
            BetterLoadingScreen.log.info("Not using custom tooltips");
            locale = Minecraft.getMinecraft().getLanguageManager().getCurrentLanguage().getLanguageCode();
            BetterLoadingScreen.log.debug("Locale is: " + locale);
            if (locale.length() > 5) {
                BetterLoadingScreen.log.debug("locale before trimming: " + locale);
                locale = locale.substring(0, 5);
            }
        } else {
            locale = customTipFilename;
            BetterLoadingScreen.log.info("Using custom tooltips, name: " + locale);
        }
        // BetterLoadingScreen.log.trace("Language is: " + locale);
        File tipsCheck = new File("./config/Betterloadingscreen/tips/" + locale + ".txt");
        if (tipsCheck.exists()) {
            BetterLoadingScreen.log.debug("Tips file exists");
            try {
                // log.info("Using locale " + locale + "(3)");
                randomTips = readTipsFile("./config/Betterloadingscreen/tips/" + locale + ".txt");
                Random rand = new Random();
                tip = randomTips[rand.nextInt(randomTips.length)];
                // BetterLoadingScreen.log.trace("choosing first tip: "+tip);
                // hmm trying to schedule tip changing
                if (!scheduledTipExecSet) {
                    // BetterLoadingScreen.log.trace("Setting tip exec");
                    // BetterLoadingScreen.log.trace("List of tips length: "+String.valueOf(randomTips.length));
                    scheduledTipExecSet = true;
                    tipExec = Executors.newSingleThreadScheduledExecutor();
                    tipExec.scheduleAtFixedRate(new Runnable() {

                        @Override
                        public void run() {
                            tip = randomTooltip(tip);
                        }
                    }, tipsChangeFrequency, tipsChangeFrequency, TimeUnit.SECONDS);
                }
            } catch (IOException e) {
                BetterLoadingScreen.log.error("./config/Betterloadingscreen/tips/" + locale + ".txt");
                e.printStackTrace();
            }
        } else {
            try {
                // BetterLoadingScreen.log.trace("Using locale " + locale + "(4)");
                tipsCheck = new File("./config/Betterloadingscreen/tips/" + locale + ".txt");
                // BetterLoadingScreen.log.trace("Checking if "+locale+".txt exists");
                if (tipsCheck.exists()) {
                    // BetterLoadingScreen.log.trace("Using locale " + locale + "(5)");
                    randomTips = readTipsFile("./config/Betterloadingscreen/" + locale + ".txt");
                } else {
                    tipsCheck = new File("./config/Betterloadingscreen/tips/en_US.txt");
                    if (!tipsCheck.exists()) {
                        // BetterLoadingScreen.log.trace("Placing tips");
                        placeTipsFile();
                    }
                    randomTips = readTipsFile("./config/Betterloadingscreen/tips/en_US.txt");
                }
                Random rand = new Random();
                tip = randomTips[rand.nextInt(randomTips.length)];
                // BetterLoadingScreen.log.trace("choosing first tip: "+tip);
                if (!scheduledTipExecSet) {
                    // BetterLoadingScreen.log.trace("Setting tip exec");
                    // BetterLoadingScreen.log.trace("List of tips length: "+String.valueOf(randomTips.length));
                    scheduledTipExecSet = true;
                    tipExec = Executors.newSingleThreadScheduledExecutor();
                    tipExec.scheduleAtFixedRate(new Runnable() {

                        @Override
                        public void run() {
                            tip = randomTooltip(tip);
                        }
                    }, tipsChangeFrequency, tipsChangeFrequency, TimeUnit.SECONDS);
                }
            } catch (IOException e) {
                BetterLoadingScreen.log.error("Error handling new tips file");
                e.printStackTrace();
            }
        }
    }

    // Minecraft's display hasn't been created yet, so don't bother trying to do anything now
    @Override
    public void open(Configuration cfg) {
        mc = Minecraft.getMinecraft();
        String n = System.lineSeparator();
        // Open the normal config
        /*
         * How configs work: String commentBruh = "bruh" + "\n"; String bruh = cfg.getString("bruhissimo", "general",
         * "false", commentBruh); BetterLoadingScreen.log.trace("Bruh is: " + bruh);
         */

        String comment4 = "What sound to play when loading is complete. Default is the level up sound (" + defaultSound
                + ")";
        sound = cfg.getString("sound", "general", defaultSound, comment4);

        comment4 = "What font texture to use? Special Cases:" + n
                + " - If you use the Russian mod \"Client Fixer\" then change this to \"textures/font/ascii_fat.png\""
                + n
                + "Note: if a resourcepack adds a font, it will be used by BLS.";
        fontTexture = cfg.getString("font", "general", defaultFontTexture, comment4);

        String comment5 = "Path to background resource." + n
                + "You can use a resourcepack or resource loader for custom resources.";
        background = cfg.getString("background", "layout", background, comment5);
        String comment6 = "Path to logo/title resource";
        title = cfg.getString("title", "layout", title, comment6);
        String comment7 = "Logo coordinates in image and position." + n
                + "the first four values indicate where the logo is located on the image (you could use a spritesheet),"
                + n
                + "the four next ones tell where the image will be located on screen like this:"
                + n
                + "[xLocation, yLocation, xWidth, yWidth, xLocation, yLocation, xWidth, yWidth]"
                + n
                + "The same is used for other images, except the background, which is fullscreen. Please ALWAYS provide"
                + n
                + "an image, a transparent one if you want even. BLS provides 'transparent.png'";
        titlePos = stringToIntArray(cfg.getString("titlePos", "layout", intArrayToString(titlePos), comment7));

        // Main Loading Bar Static
        String comment8 = "Path to main loading bar resource";
        progress = cfg.getString("mainProgressBar", "layout", progress, comment8);
        String comment9 = "Main loading bar position";
        progressPos = stringToIntArray(
                cfg.getString("mainProgressBarPos", "layout", intArrayToString(progressPos), comment9));
        // Main Loading Bar Animated
        String comment10 = "Path to animated main loading bar resource";
        progressAnimated = cfg.getString("mainProgressBarAnimated", "layout", progressAnimated, comment10);
        String comment11 = "Main animated loading bar position";
        progressPosAnimated = stringToIntArray(
                cfg.getString(
                        "mainProgressBarPosAnimated",
                        "layout",
                        intArrayToString(progressPosAnimated),
                        comment11));
        memoryPos = stringToIntArray(
                cfg.getString("memoryBarPos", "layout", intArrayToString(memoryPos), "Memory bar position"));
        memoryPosAnimated = stringToIntArray(
                cfg.getString(
                        "memoryBarPosAnimated",
                        "layout",
                        intArrayToString(memoryPosAnimated),
                        "Memory bar animated position"));
        // Main Loading Bar Text
        String comment12 = "Main loading bar text position. The four values are for position.";
        progressTextPos = stringToIntArray(
                cfg.getString("mainProgressBarTextPos", "layout", intArrayToString(progressTextPos), comment12));
        // Main Loading Bar Percentage
        String comment13 = "Main loading bar percentage position";
        progressPercentagePos = stringToIntArray(
                cfg.getString(
                        "mainProgressBarPercentagePos",
                        "layout",
                        intArrayToString(progressPercentagePos),
                        comment13));

        // Material Loading Bar Static
        String comment14 = "Path to materials loading bar";
        GTprogress = cfg.getString("materialProgressBar", "layout", GTprogress, comment14);
        String comment15 = "Material loading bar position";
        GTprogressPos = stringToIntArray(
                cfg.getString("GTProgressBarPos", "layout", intArrayToString(GTprogressPos), comment15));
        // Material Loading Bar Animated
        String comment16 = "Path to animated materials loading bar";
        GTprogressAnimated = cfg.getString("materialProgressBarAnimated", "layout", GTprogress, comment16);
        String comment17 = "Material animated loading bar position";
        GTprogressPosAnimated = stringToIntArray(
                cfg.getString(
                        "GTProgressBarPosAnimated",
                        "layout",
                        intArrayToString(GTprogressPosAnimated),
                        comment17));
        // Material Loading Bar Text
        String comment18 = "Material loading bar text position. The two values are for position (x and y).";
        GTprogressTextPos = stringToIntArray(
                cfg.getString("materialProgressBarTextPos", "layout", intArrayToString(GTprogressTextPos), comment18));
        // Main Loading Bar Percentage
        String comment19 = "Material loading bar percentage position";
        GTprogressPercentagePos = stringToIntArray(
                cfg.getString(
                        "materialProgressBarPercentagePos",
                        "layout",
                        intArrayToString(GTprogressPercentagePos),
                        comment19));

        // Color of the two dynamic bars
        String comment39 = "color of main and GT material dynamic loading bar (Use ffffff (white)) if you don't want to color it";
        loadingBarsColor = cfg.getString("loadingBarsColor", "layout", loadingBarsColor, comment39);
        String comment40 = "Transparency of main and GT material dynamic loading bar";
        loadingBarsAlpha = cfg.getFloat("loadingBarsAlpha", "layout", loadingBarsAlpha, 0, 1, comment40);

        // Some text properties
        String comment20 = "Whether the text should be rendered with a shadow. Recommended, unless the background is really dark";
        textShadow = cfg.getBoolean("textShadow", "layout", textShadow, comment20);
        String comment21 = "Color of text in hexadecimal format";
        textColor = cfg.getString("textColor", "layout", textColor, comment21);

        // Stuff related to random backgrounds
        String comment22 = "Whether display a random background from the random backgrounds list";
        randomBackgrounds = cfg.getBoolean("randomBackgrounds", "layout", randomBackgrounds, comment22);
        String comment23 = "List of paths to backgrounds that will be used if randomBackgrounds is true." + n
                + "The paths must be separated by commas.";
        randomBackgroundArray = parseBackgroundCFGListToArray(
                (cfg.getString(
                        "backgroundList",
                        "layout",
                        parseBackgroundArraytoCFGList(randomBackgroundArray),
                        comment23)));

        // Stuff related to blending
        String comment24 = "Whether backgrounds should change randomly during loading. They are taken from the random background list";
        blendingEnabled = cfg.getBoolean("backgroundChanging", "changing background", blendingEnabled, comment24);
        String comment25 = "Time in milliseconds between each image change (smooth blend).";
        blendTimeMillis = cfg
                .getFloat("blendTimeMilliseconds", "changing background", blendTimeMillis, 0, 30_000, comment25);
        /*
         * NOBODY EXPECTS THE SPANISH INQUISITION!
         */
        String comment26 = "How many seconds between background changes";
        changeFrequency = cfg.getInt("changeFrequency", "changing background", changeFrequency, 1, 9000, comment26);
        String comment28 = "No, don't touch that!";
        shouldGLClear = cfg.getBoolean("shouldGLClear", "changing background", shouldGLClear, comment28);

        // salt
        String comment29 = "If you want to save a maximum of time on your loading time but don't want to face a black screen, try this.";
        salt = cfg.getBoolean("salt", "skepticism", salt, comment29);

        // imgur
        String comment30 = "Set to true if you want to load images from an imgur gallery and use them as backgrounds.";
        useImgur = cfg.getBoolean("useImgur", "imgur", useImgur, comment30);

        // tips
        String comment32 = "Set to true if you want to display random tips. Tips are stored in a separate file";
        tipsEnabled = cfg.getBoolean("tipsEnabled", "tips", tipsEnabled, comment32);
        String comment34 = "Base text position. Can be TOP_CENTER, TOP_RIGHT, CENTER_LEFT, CENTER, CENTER_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER or BOTTOM_RIGHT."
                + n
                + "Note: Other elements use CENTER, if you really need, ask to implement this base position option for any other element.";
        baseTipsTextPos = cfg.getString("baseTipsTextPos", "tips", baseTipsTextPos, comment34);
        String comment35 = "Tips text position";
        tipsTextPos = stringToIntArray(cfg.getString("tipsTextPos", "tips", intArrayToString(tipsTextPos), comment35));
        String comment36 = "Whether the tips text should be rendered with a shadow.";
        tipsTextShadow = cfg.getBoolean("tipsTextShadow", "tips", tipsTextShadow, comment36);
        String comment37 = "Color of tips text in hexadecimal format";
        tipsColor = cfg.getString("tipsTextColor", "tips", tipsColor, comment37);
        String comment38 = "Time in seconds between tip changes";
        tipsChangeFrequency = cfg.getInt("tipsChangeFrequency", "tips", tipsChangeFrequency, 1, 9000, comment38);
        String comment41 = "Set to true if you want a custom tips file/different locale than your Minecraft one.";
        useCustomTips = cfg.getBoolean("useCustomTips", "tips", useCustomTips, comment41);
        String comment42 = "Custom tips file name, place it in config/Betterloadingscreen/tips. " + n
                + "Don't include the \".txt\". Example: \"myTipFile\"";
        customTipFilename = cfg.getString("customTipFilename", "tips", customTipFilename, comment42);

        try {
            lbRGB[0] = (float) (Color.decode("#" + loadingBarsColor).getRed() & 255) / 255.0f; // Color.decode("#" +
            // loadingBarsColor).getRed();
            lbRGB[1] = (float) (Color.decode("#" + loadingBarsColor).getGreen() & 255) / 255.0f; // Color.decode("#" +
            // loadingBarsColor).getGreen();
            lbRGB[2] = (float) (Color.decode("#" + loadingBarsColor).getBlue() & 255) / 255.0f; // Color.decode("#" +
            // loadingBarsColor).getBlue();
            // BetterLoadingScreen.log.debug("The color: " + String.valueOf(lbRGB[0]) + ";" + String.valueOf(lbRGB[1]) +
            // ";" + String.valueOf(lbRGB[2]));
        } catch (Exception e) {
            lbRGB[0] = 1;
            lbRGB[1] = 0.5176471f;
            lbRGB[2] = 0;
            BetterLoadingScreen.log.warn("Invalid loading bar color, setting default");
        }
        /*
         * if (useImgur) { BetterLoadingScreen.log.trace("2hmmm"); List<Thread> workers = Stream .generate(() -> new
         * Thread(new DlAllImages(countDownLatch))) .limit(1) .collect(toList()); workers.forEach(Thread::start); try {
         * countDownLatch.await(); } catch (InterruptedException e) { e.printStackTrace(); } }
         */

        if (salt) {
            blendingEnabled = false;
        }

        // Add ourselves as a resource pack
        if (!preview) {
            if (!ProgressDisplayer.coreModLocation.isDirectory())
                myPack = new FMLFileResourcePack(ProgressDisplayer.modContainer);
            else myPack = new FMLFolderResourcePack(ProgressDisplayer.modContainer);
            getOnlyList().add(myPack);
            mc.refreshResources();
        }

        handleTips();

        if (randomBackgrounds && !salt) {
            // BetterLoadingScreen.log.trace("choosing first random bg");
            Random rand = new Random();
            background = randomBackgroundArray[rand.nextInt(randomBackgroundArray.length)];

            /// timer
            if (!scheduledBackgroundExecSet) {
                // BetterLoadingScreen.log.trace("Setting background exec");
                scheduledBackgroundExecSet = true;
                backgroundExec = Executors.newSingleThreadScheduledExecutor();
                backgroundExec.scheduleAtFixedRate(new Runnable() {

                    @Override
                    public void run() {
                        if (!blending /*
                                       * && !isRegisteringBartWorks && !isRegisteringGTmaterials &&
                                       * !isReplacingVanillaMaterials
                                       */) {
                            MinecraftDisplayer.blendingJustSet = true;
                            MinecraftDisplayer.blendAlpha = 1;
                            MinecraftDisplayer.blendStartMillis = System.currentTimeMillis();
                            MinecraftDisplayer.blending = true;
                        }
                    }
                }, changeFrequency, changeFrequency, TimeUnit.SECONDS);

                if (useImgur) {
                    imgurCacheManager = new ImgurCacheManager();
                    imgurCacheManager.loadConfig(cfg);

                    List<String> imgurBackgrounds = new ArrayList<>();
                    imgurCacheManager.setupImgurGallery(res -> {
                        // Override the default background with the first image we get, otherwise the image will only
                        // be visible after the first blend occurs
                        if (imgurBackgrounds.isEmpty()) background = res.toString();

                        // Progressively add each image to the list of random backgrounds
                        imgurBackgrounds.add(res.toString());
                        randomBackgroundArray = imgurBackgrounds.toArray(new String[0]);
                    });
                }
            }
        }

        // Open the special config directory
        // File configDir = new File("./config/Betterloadingscreen");
        File configDir = new File("./config");
        /*
         * if (!configDir.exists()) { configDir.mkdirs(); }
         */
    }

    @Override
    public void displayProgress(String text, float percent) {
        currentText = text;
        currentPercent = percent;
        if (splashRenderThread == null) {
            try {
                loadingDrawable = new SharedDrawable(Display.getDrawable());
                Display.getDrawable().releaseContext();
                loadingDrawable.makeCurrent();
            } catch (LWJGLException e) {
                e.printStackTrace();
                throw new RuntimeException(e); // work around checked exceptions
            }
            splashRenderThread = new Thread(new Runnable() {

                /**
                 * Has to be locked while running Display.update()
                 */
                Semaphore fmlMutex;

                @Override
                public void run() {
                    try {
                        Field f = SplashProgress.class.getDeclaredField("mutex");
                        f.setAccessible(true);
                        fmlMutex = (Semaphore) f.get(null);
                        Display.getDrawable().makeCurrent();
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                    while (!MinecraftDisplayer.this.splashRenderKillSwitch) {
                        resetGlState();
                        try {
                            displayProgressInWorkerThread(currentText, currentPercent);
                        } catch (Exception e) {
                            BetterLoadingScreen.log.warn("BLS splash error: ", e);
                        }

                        fmlMutex.acquireUninterruptibly();
                        Display.update();
                        fmlMutex.release();
                        Display.sync(60);
                    }
                    resetGlState();
                    try {
                        Display.getDrawable().releaseContext();
                    } catch (LWJGLException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }

                private void resetGlState() {
                    Minecraft mc = Minecraft.getMinecraft();
                    int w = Display.getWidth();
                    int h = Display.getHeight();
                    mc.displayWidth = w;
                    mc.displayHeight = h;
                    GL11.glClearColor(0, 0, 0, 1);
                    GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                    GL11.glEnable(GL_DEPTH_TEST);
                    GL11.glDepthFunc(GL_LEQUAL);
                    GL11.glEnable(GL_ALPHA_TEST);
                    GL11.glAlphaFunc(GL_GREATER, .1f);
                    GL11.glViewport(0, 0, w, h);
                    GL11.glMatrixMode(GL_PROJECTION);
                    GL11.glLoadIdentity();
                    GL11.glOrtho(320 - w / 2, 320 + w / 2, 240 + h / 2, 240 - h / 2, -1, 1);
                    GL11.glMatrixMode(GL_MODELVIEW);
                    GL11.glLoadIdentity();
                }
            });
            splashRenderThread.setName("BLS Splash renderer");
            splashRenderThread.setDaemon(true);
            splashRenderThread.setUncaughtExceptionHandler(
                    (Thread t, Throwable e) -> {
                        BetterLoadingScreen.log.error("BetterLodingScreen thread exception", e);
                    });
            splashRenderThread.start();
            if (splashRenderThread.getState() == Thread.State.TERMINATED) {
                throw new IllegalStateException("BetterLoadingScreen splash thread terminated upon start");
            }
        }
    }

    public void displayProgressInWorkerThread(String text, float percent) {
        if (!salt) {
            /*
             * if (tipsEnabled && ((!isRegisteringBartWorks && !isRegisteringGTmaterials && !isReplacingVanillaMaterials
             * && tipCounter > tipsChangeFrequency) || ((isRegisteringBartWorks || isRegisteringGTmaterials ||
             * isReplacingVanillaMaterials) && tipCounter > tipsChangeFrequency*secondBarToolTipMultiplier))) {
             * tipCounter = 0; tip = randomTooltip(tip); }
             */
            if (alexiil.mods.load.MinecraftDisplayer.isRegisteringGTmaterials || isReplacingVanillaMaterials
                    || isRegisteringBartWorks) {
                if (!tipsEnabled) {
                    images = new ImageRender[11];
                    nonStaticElementsToGo = 10;
                } else {
                    images = new ImageRender[12];
                    nonStaticElementsToGo = 11;
                }
                // background
                if (!background.equals("")) {
                    images[0] = new ImageRender(
                            background,
                            EPosition.TOP_LEFT,
                            EType.STATIC_BLENDED,
                            new Area(0, 0, 256, 256),
                            new Area(0, 0, 0, 0));
                } else {
                    images[0] = new ImageRender(
                            "betterloadingscreen:textures/transparent.png",
                            EPosition.TOP_LEFT,
                            EType.STATIC,
                            new Area(0, 0, 256, 256),
                            new Area(0, 0, 10, 10));
                }
                // Logo
                if (!title.equals("")) {
                    images[1] = new ImageRender(
                            title,
                            EPosition.CENTER,
                            EType.STATIC,
                            new Area(titlePos[0], titlePos[1], titlePos[2], titlePos[3]),
                            new Area(titlePos[4], titlePos[5], titlePos[6], titlePos[7]));
                } else {
                    images[1] = new ImageRender(
                            "betterloadingscreen:textures/transparent.png",
                            EPosition.TOP_LEFT,
                            EType.STATIC,
                            new Area(0, 0, 256, 256),
                            new Area(0, 0, 10, 10));
                }
                // GT progress text
                images[2] = new ImageRender(
                        fontTexture,
                        EPosition.CENTER,
                        EType.DYNAMIC_TEXT_STATUS,
                        null,
                        new Area(GTprogressTextPos[0], GTprogressTextPos[1], 0, 0),
                        "ffffff",
                        null,
                        "");
                // GT progress percentage text
                images[3] = new ImageRender(
                        fontTexture,
                        EPosition.CENTER,
                        EType.DYNAMIC_TEXT_PERCENTAGE,
                        null,
                        new Area(GTprogressPercentagePos[0], GTprogressPercentagePos[1], 0, 0),
                        "ffffff",
                        null,
                        "");
                // Static NORMAL bar image
                images[4] = new ImageRender(
                        progress,
                        EPosition.CENTER,
                        EType.STATIC,
                        new Area(progressPos[0], progressPos[1], progressPos[2], progressPos[3]),
                        new Area(progressPos[4], progressPos[5], progressPos[6], progressPos[7]));
                // Dynamic NORMAL bar image (yellow thing)
                images[5] = new ImageRender(
                        progress,
                        EPosition.CENTER,
                        EType.DYNAMIC_PERCENTAGE,
                        new Area(
                                progressPosAnimated[0],
                                progressPosAnimated[1],
                                progressPosAnimated[2],
                                progressPosAnimated[3]),
                        new Area(
                                progressPosAnimated[4],
                                progressPosAnimated[5],
                                progressPosAnimated[6],
                                progressPosAnimated[7]));
                // NORMAL progress text
                images[6] = new ImageRender(
                        fontTexture,
                        EPosition.CENTER,
                        EType.DYNAMIC_TEXT_STATUS,
                        null,
                        new Area(progressTextPos[0], progressTextPos[1], 0, 0),
                        "ffffff",
                        null,
                        "");
                // NORMAL progress percentage text
                images[7] = new ImageRender(
                        fontTexture,
                        EPosition.CENTER,
                        EType.DYNAMIC_TEXT_PERCENTAGE,
                        null,
                        new Area(progressPercentagePos[0], progressPercentagePos[1], 0, 0),
                        "ffffff",
                        null,
                        "");
                // Static GT bar image
                images[8] = new ImageRender(
                        GTprogress,
                        EPosition.CENTER,
                        EType.STATIC,
                        new Area(GTprogressPos[0], GTprogressPos[1], GTprogressPos[2], GTprogressPos[3]),
                        new Area(GTprogressPos[4], GTprogressPos[5], GTprogressPos[6], GTprogressPos[7]));
                // Dynamic GT bar image (yellow thing)
                images[9] = new ImageRender(
                        GTprogress,
                        EPosition.CENTER,
                        EType.DYNAMIC_PERCENTAGE,
                        new Area(
                                GTprogressPosAnimated[0],
                                GTprogressPosAnimated[1],
                                GTprogressPosAnimated[2],
                                GTprogressPosAnimated[3]),
                        new Area(
                                GTprogressPosAnimated[4],
                                GTprogressPosAnimated[5],
                                GTprogressPosAnimated[6],
                                GTprogressPosAnimated[7]));
                ///
                if (!tipsEnabled) {
                    // Hmmm no idea what that is, maybe the thing that clears the screen
                    images[10] = new ImageRender(null, null, EType.CLEAR_COLOUR, null, null, "ffffff", null, "");
                } else {
                    // Tips text
                    images[10] = new ImageRender(
                            fontTexture,
                            EPosition.valueOf(baseTipsTextPos),
                            EType.TIPS_TEXT,
                            null,
                            new Area(tipsTextPos[0], tipsTextPos[1], 0, 0),
                            "000000",
                            tip,
                            "");
                    // Hmmm no idea what that is, maybe the thing that clears the screen
                    images[11] = new ImageRender(null, null, EType.CLEAR_COLOUR, null, null, "ffffff", null, "");
                }
                //
            } else {
                if (!tipsEnabled) {
                    images = new ImageRender[7];
                    nonStaticElementsToGo = 6;
                } else {
                    images = new ImageRender[8];
                    nonStaticElementsToGo = 7;
                }
                // background
                if (!background.equals("")) {
                    images[0] = new ImageRender(
                            background,
                            EPosition.TOP_LEFT,
                            EType.STATIC_BLENDED,
                            new Area(0, 0, 256, 256),
                            new Area(0, 0, 0, 0));
                } else {
                    images[0] = new ImageRender(
                            "betterloadingscreen:textures/transparent.png",
                            EPosition.TOP_LEFT,
                            EType.STATIC,
                            new Area(0, 0, 256, 256),
                            new Area(0, 0, 10, 10));
                }
                // Logo
                if (!title.equals("")) {
                    images[1] = new ImageRender(
                            title,
                            EPosition.CENTER,
                            EType.STATIC,
                            new Area(titlePos[0], titlePos[1], titlePos[2], titlePos[3]),
                            new Area(titlePos[4], titlePos[5], titlePos[6], titlePos[7]));
                } else {
                    images[1] = new ImageRender(
                            "betterloadingscreen:textures/transparent.png",
                            EPosition.TOP_LEFT,
                            EType.STATIC,
                            new Area(0, 0, 256, 256),
                            new Area(0, 0, 10, 10));
                }
                // NORMAL progress text
                images[2] = new ImageRender(
                        fontTexture,
                        EPosition.CENTER,
                        EType.DYNAMIC_TEXT_STATUS,
                        null,
                        new Area(progressTextPos[0], progressTextPos[1], 0, 0),
                        "ffffff",
                        null,
                        "");
                // NORMAL progress percentage text
                images[3] = new ImageRender(
                        fontTexture,
                        EPosition.CENTER,
                        EType.DYNAMIC_TEXT_PERCENTAGE,
                        null,
                        new Area(progressPercentagePos[0], progressPercentagePos[1], 0, 0),
                        "ffffff",
                        null,
                        "");
                // Static NORMAL bar image
                images[4] = new ImageRender(
                        progress,
                        EPosition.CENTER,
                        EType.STATIC,
                        new Area(progressPos[0], progressPos[1], progressPos[2], progressPos[3]),
                        new Area(progressPos[4], progressPos[5], progressPos[6], progressPos[7]));
                // Dynamic NORMAL bar image (yellow thing)
                images[5] = new ImageRender(
                        progress,
                        EPosition.CENTER,
                        EType.DYNAMIC_PERCENTAGE,
                        new Area(
                                progressPosAnimated[0],
                                progressPosAnimated[1],
                                progressPosAnimated[2],
                                progressPosAnimated[3]),
                        new Area(
                                progressPosAnimated[4],
                                progressPosAnimated[5],
                                progressPosAnimated[6],
                                progressPosAnimated[7]));
                if (!tipsEnabled) {
                    images[6] = new ImageRender(null, null, EType.CLEAR_COLOUR, null, null, "ffffff", null, "");
                } else {
                    images[6] = new ImageRender(
                            fontTexture,
                            EPosition.valueOf(baseTipsTextPos),
                            EType.TIPS_TEXT,
                            null,
                            new Area(tipsTextPos[0], tipsTextPos[1], 0, 0),
                            tipsColor,
                            tip,
                            "");
                    images[7] = new ImageRender(null, null, EType.CLEAR_COLOUR, null, null, "ffffff", null, "");
                }
            }
        } else {
            shouldGLClear = false;
            textShadow = false;
            textColor = "000000";
            if (!saltBGhasBeenRendered) {
                images = new ImageRender[2];
                images[0] = new ImageRender(
                        "betterloadingscreen:textures/salt.png",
                        EPosition.TOP_LEFT,
                        EType.STATIC,
                        new Area(0, 0, 256, 256),
                        new Area(0, 0, 0, 0));
                images[1] = new ImageRender(
                        fontTexture,
                        EPosition.BOTTOM_LEFT,
                        EType.DYNAMIC_TEXT_STATUS,
                        null,
                        new Area(10, 10, 0, 0),
                        "000000",
                        null,
                        "");
            } else {
                images = new ImageRender[0];
            }
        }

        resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);

        preDisplayScreen();

        int imageCounter = 0;

        if (!isRegisteringGTmaterials && !isReplacingVanillaMaterials && !isRegisteringBartWorks) {
            lastPercent = percent;
        }

        for (ImageRender image : images) {
            // Warning: do not add underline/strikethrough styling to the text, as that can cause Tesselator data races
            // between threads
            if (salt) {
                drawImageRender(image, "Minecraft is loading, please wait...", percent);
            } else if (image != null && !(imageCounter > 4
                    && (isRegisteringGTmaterials || isReplacingVanillaMaterials || isRegisteringBartWorks)
                    && imageCounter < 9)) {
                        drawImageRender(image, text, percent);
                    } else
                if (image != null && isRegisteringGTmaterials && !isNice) {
                    drawImageRender(image, " Post Initialization: Registering Gregtech materials", lastPercent);

                } else if (image != null && isRegisteringGTmaterials && isNice) {
                    drawImageRender(image, " Post Initialization: Registering nice GregTech materials", lastPercent);
                    if (!hasSaidNice) {
                        hasSaidNice = true;
                        BetterLoadingScreen.log.info("Yeah, that's nice, funni number");
                    }
                } else if (isReplacingVanillaMaterials) {
                    drawImageRender(
                            image,
                            " Post Initialization: GregTech replacing Vanilla materials in recipes",
                            lastPercent);
                } else if (isRegisteringBartWorks) {
                    drawImageRender(image, " Post Initialization: Registering BartWorks materials", lastPercent);
                }
            imageCounter++;
        }

        // Draw memory usage bar
        final Runtime rt = Runtime.getRuntime();
        final long maxMem = Long.max(1, rt.maxMemory() / (1024 * 1024));
        final long usedMem = Long.max(1, (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        final String memText = String
                .format(Translation.translate("betterloadingscreen.memory_usage"), usedMem, maxMem);
        drawImageRender(
                new ImageRender(
                        progress,
                        EPosition.TOP_CENTER,
                        EType.STATIC,
                        new Area(memoryPos[0], memoryPos[1], memoryPos[2], memoryPos[3]),
                        new Area(memoryPos[4], memoryPos[5], memoryPos[6], memoryPos[7]),
                        "ffffff",
                        null,
                        null),
                null,
                0.0);
        drawImageRender(
                new ImageRender(
                        fontTexture,
                        EPosition.TOP_CENTER,
                        EType.DYNAMIC_TEXT_STATUS,
                        new Area(memoryPos[0], memoryPos[1], memoryPos[2], memoryPos[3]),
                        new Area(memoryPos[4], memoryPos[5] - 10, memoryPos[6], memoryPos[7]),
                        "ffffff",
                        null,
                        null),
                memText,
                0.0);
        drawImageRender(
                new ImageRender(
                        progress,
                        EPosition.TOP_CENTER,
                        EType.DYNAMIC_PERCENTAGE,
                        new Area(
                                memoryPosAnimated[0],
                                memoryPosAnimated[1],
                                memoryPosAnimated[2],
                                memoryPosAnimated[3]),
                        new Area(
                                memoryPosAnimated[4],
                                memoryPosAnimated[5],
                                memoryPosAnimated[6],
                                memoryPosAnimated[7]),
                        "ffffff",
                        null,
                        null),
                null,
                (double) usedMem / (double) maxMem);
    }

    private FontRenderer fontRenderer(String fontTexture) {
        if (fontRenderers.containsKey(fontTexture)) {
            return fontRenderers.get(fontTexture);
        }
        FontRenderer font = new FontRenderer(mc.gameSettings, new ResourceLocation(fontTexture), textureManager, false);
        font.onResourceManagerReload(mc.getResourceManager());
        font.setUnicodeFlag(mc.func_152349_b());
        if (!preview) {
            mc.refreshResources();
            font.onResourceManagerReload(mc.getResourceManager());
        }
        fontRenderers.put(fontTexture, font);
        return font;
    }

    public void drawImageRender(ImageRender render, String text, double percent) {
        int startX = render.transformX(resolution.getScaledWidth());
        int startY = render.transformY(resolution.getScaledHeight());
        int PWidth = 0;
        int PHeight = 0;
        int intColor = Integer.parseInt(textColor, 16);
        if (render.position != null) {
            PWidth = render.position.width == 0 ? resolution.getScaledWidth() : render.position.width;
            PHeight = render.position.height == 0 ? resolution.getScaledHeight() : render.position.height;
        }
        GL11.glColor4f(render.getRed(), render.getGreen(), render.getBlue(), 1);
        switch (render.type) {
            case DYNAMIC_PERCENTAGE: {
                ResourceLocation res = new ResourceLocation(render.resourceLocation);
                textureManager.bindTexture(res);
                double visibleWidth = PWidth * percent;
                double textureWidth = render.texture.width * percent;
                GL11.glColor4f(lbRGB[0], lbRGB[1], lbRGB[2], loadingBarsAlpha);
                drawRect(
                        startX,
                        startY,
                        visibleWidth,
                        PHeight,
                        render.texture.x,
                        render.texture.y,
                        textureWidth,
                        render.texture.height);
                GL11.glColor4f(1, 1, 1, 1);
                break;
            }
            case DYNAMIC_TEXT_PERCENTAGE: {
                FontRenderer font = fontRenderer(render.resourceLocation);
                String percentage = (int) (percent * 100) + "%";
                int width = font.getStringWidth(percentage);
                startX = render.positionType.transformX(render.position.x, resolution.getScaledWidth() - width);
                startY = render.positionType
                        .transformY(render.position.y, resolution.getScaledHeight() - font.FONT_HEIGHT);
                if (textShadow) {
                    font.drawStringWithShadow(percentage, startX, startY, /* render.getColour() */ intColor);
                } else {
                    drawString(font, percentage, startX, startY, intColor);
                }
                break;
            }
            case DYNAMIC_TEXT_STATUS: {
                FontRenderer font = fontRenderer(render.resourceLocation);
                int width = font.getStringWidth(text);
                startX = render.positionType.transformX(render.position.x, resolution.getScaledWidth() - width);
                startY = render.positionType
                        .transformY(render.position.y, resolution.getScaledHeight() - font.FONT_HEIGHT);
                ////////////////
                // This allows to draw each char separately.
                if (experimental) {
                    int currentX = startX;
                    for (int i = 0; i < text.length(); i++) {
                        // drawString(font., String.valueOf(text.charAt(i)), currentX, startY, intColor);
                        double scale = 2;
                        BetterLoadingScreen.log.debug("currentX before scale: " + currentX);
                        GL11.glScaled(scale, scale, scale);
                        BetterLoadingScreen.log.debug("currentX after scale: " + currentX);
                        drawString(
                                font,
                                String.valueOf(text.charAt(i)),
                                (int) (currentX / scale),
                                (int) (startY / scale), /* intColor */
                                0);
                        GL11.glScaled(1, 1, 1);
                        currentX += font.getCharWidth(text.charAt(i));
                    }
                }
                ///////////////
                else {
                    if (textShadow) {
                        font.drawStringWithShadow(text, startX, startY, intColor);
                    } else {
                        drawString(font, text, startX, startY, intColor);
                    }
                }
                break;
            }
            case STATIC_TEXT: {
                FontRenderer font = fontRenderer(render.resourceLocation);
                int width = font.getStringWidth(render.text);
                int startX1 = render.positionType.transformX(render.position.x, resolution.getScaledWidth() - width);
                int startY1 = render.positionType
                        .transformY(render.position.y, resolution.getScaledHeight() - font.FONT_HEIGHT);
                if (textShadow) {
                    font.drawStringWithShadow(render.text, startX1, startY1, intColor);
                } else {
                    drawString(font, render.text, startX1, startY1, intColor);
                }
                break;
            }
            case TIPS_TEXT: {
                FontRenderer font = fontRenderer(render.resourceLocation);
                int width = font.getStringWidth(render.text);
                int startX1 = render.positionType.transformX(render.position.x, resolution.getScaledWidth() - width);
                // BetterLoadingScreen.log.trace("startX1 normal: "+startX1);
                int startY1 = render.positionType
                        .transformY(render.position.y, resolution.getScaledHeight() - font.FONT_HEIGHT);
                if (tipsTextShadow) {
                    font.drawStringWithShadow(render.text, startX1, startY1, Integer.parseInt(tipsColor, 16));
                } else {
                    drawString(font, render.text, startX1, startY1, Integer.parseInt(tipsColor, 16));
                }
                break;
            }
            case STATIC:
            case STATIC_BLENDED: {
                if (blending && render.type == EType.STATIC_BLENDED) {
                    if (blendingJustSet) {
                        blendingJustSet = false;
                        newBlendImage = randomBackground(render.resourceLocation);
                    }

                    if (blendTimeMillis < 1.f) {
                        blendAlpha = 0.f;
                    } else {
                        blendAlpha = Float.max(
                                0.f,
                                1.0f - (float) (System.currentTimeMillis() - blendStartMillis) / blendTimeMillis);
                    }
                    if (blendAlpha <= 0.f) {
                        blending = false;
                        background = newBlendImage;
                    }

                    GL11.glColor4f(render.getRed(), render.getGreen(), render.getBlue(), blendAlpha);
                    bindTexture(render.resourceLocation);
                    drawRect(
                            startX,
                            startY,
                            PWidth,
                            PHeight,
                            render.texture.x,
                            render.texture.y,
                            render.texture.width,
                            render.texture.height);

                    ImageRender render2 = new ImageRender(
                            newBlendImage,
                            EPosition.TOP_LEFT,
                            EType.STATIC,
                            new Area(0, 0, 256, 256),
                            new Area(0, 0, 0, 0));
                    GL11.glColor4f(render2.getRed(), render2.getGreen(), render2.getBlue(), 1.f - blendAlpha);
                    bindTexture(render2.resourceLocation);
                    drawRect(
                            startX,
                            startY,
                            PWidth,
                            PHeight,
                            render2.texture.x,
                            render2.texture.y,
                            render2.texture.width,
                            render2.texture.height);
                    break;
                } else {
                    GL11.glColor4f(render.getRed(), render.getGreen(), render.getBlue(), 1F);
                    bindTexture(render.resourceLocation);
                    drawRect(
                            startX,
                            startY,
                            PWidth,
                            PHeight,
                            render.texture.x,
                            render.texture.y,
                            render.texture.width,
                            render.texture.height);
                    break;
                }

                // break;
            }
            case CLEAR_COLOUR: // Ignore this, as its set elsewhere
                break;
        }
    }

    private void bindTexture(String resourceLocation) {
        ResourceLocation res = new ResourceLocation(resourceLocation);

        // We cannot go through the default texture loader, because it can't load from the file system
        AbstractTexture texture = imgurCacheManager != null ? imgurCacheManager.getCachedTexture(res) : null;
        if (texture != null) {
            // Add the texture to TextureManager's cache to disable the loading logic in bindTexture
            try {
                textureManager.loadTexture(res, texture);
            } catch (Exception e) {
                BetterLoadingScreen.log.error("Failed to load imgur texture: " + res.getResourcePath(), e);
            }
        }

        textureManager.bindTexture(res);
    }

    public void drawString(FontRenderer font, String text, int x, int y, int colour) {
        font.drawString(text, x, y, colour);
        GL11.glColor4f(1, 1, 1, 1);
    }

    public void drawRect(double x, double y, double drawnWidth, double drawnHeight, double u, double v, double uWidth,
            double vHeight) {
        float f = 1 / 256F;
        // Can't use Tesselator, because the main thread can be using it simultaneously
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2d(u * f, (v + vHeight) * f);
        GL11.glVertex3d(x, y + drawnHeight, 0);
        GL11.glTexCoord2d((u + uWidth) * f, (v + vHeight) * f);
        GL11.glVertex3d(x + drawnWidth, y + drawnHeight, 0);
        GL11.glTexCoord2d((u + uWidth) * f, v * f);
        GL11.glVertex3d(x + drawnWidth, y, 0);
        GL11.glTexCoord2d(u * f, v * f);
        GL11.glVertex3d(x, y, 0);
        GL11.glEnd();
    }

    private void preDisplayScreen() {
        // BetterLoadingScreen.log.trace("Called preDisplayScreen");
        // bruh
        if (textureManager == null) {
            if (preview) {
                textureManager = mc.renderEngine;
            } else {
                textureManager = mc.renderEngine = new TextureManager(mc.getResourceManager());
                mc.refreshResources();
                textureManager.onResourceManagerReload(mc.getResourceManager());
                mc.fontRenderer = new FontRenderer(
                        mc.gameSettings,
                        new ResourceLocation("textures/font/ascii.png"),
                        textureManager,
                        false);
                if (mc.gameSettings.language != null) {
                    mc.fontRenderer.setUnicodeFlag(mc.func_152349_b());
                    LanguageManager lm = mc.getLanguageManager();
                    mc.fontRenderer.setBidiFlag(lm.isCurrentLanguageBidirectional());
                }
                mc.fontRenderer.onResourceManagerReload(mc.getResourceManager());
            }
        }
        if (fontRenderer != mc.fontRenderer) {
            fontRenderer = mc.fontRenderer;
        }
        // if (textureManager != mc.renderEngine)
        // textureManager = mc.renderEngine;
        resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int i = resolution.getScaleFactor();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(
                0.0D,
                (double) resolution.getScaledWidth(),
                (double) resolution.getScaledHeight(),
                0.0D,
                1000.0D,
                3000.0D);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL11.glTranslatef(0.0F, 0.0F, -2000.0F);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);

        GL11.glClearColor(clearRed, clearGreen, clearBlue, 1);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 1.F / 255.F);

        GL11.glColor4f(1, 1, 1, 1);
    }

    public ImageRender[] getImageData() {
        return images;
    }

    @Override
    public void close() {
        if (splashRenderThread != null && splashRenderThread.isAlive()) {
            BetterLoadingScreen.log.info("BLS Splash loading thread closing");
            splashRenderKillSwitch = true;
            try {
                loadingDrawable.releaseContext();
                splashRenderThread.join();
                Display.getDrawable().makeCurrent();
                Minecraft.getMinecraft().resize(Display.getWidth(), Display.getHeight());
            } catch (LWJGLException | InterruptedException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
        if (tipExec != null) {
            tipExec.shutdown();
        }
        if (backgroundExec != null) {
            backgroundExec.shutdown();
        }
        getOnlyList().remove(myPack);

        if (imgurCacheManager != null) {
            imgurCacheManager.cleanUp();
            imgurCacheManager = null;
        }
    }
}
