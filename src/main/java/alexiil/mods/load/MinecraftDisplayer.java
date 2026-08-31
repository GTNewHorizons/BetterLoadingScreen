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
    private boolean threadedRendering = true;
    private ImageRender[] images;

    private TextureManager textureManager = null;
    private Map<String, FontRenderer> fontRenderers = new HashMap<String, FontRenderer>();
    private FontRenderer fontRenderer = null;
    private ScaledResolution resolution = null;
    private Minecraft mc = null;
    private IResourcePack myPack;

    private float clearRed = 1;
    private float clearGreen = 1;
    private float clearBlue = 1;

    private List<String> alreadyUsedBGs = new ArrayList<>();
    private List<String> alreadyUsedTooltips = new ArrayList<>();

    private String progress = "betterloadingscreen:textures/mainProgressBar.png";
    private String progressAnimated = "betterloadingscreen:textures/mainProgressBar.png";
    private String title = "betterloadingscreen:textures/transparent.png";
    private String background = "betterloadingscreen:textures/backgrounds/01.png";

    // Coordinate format: {texture x, y, w, h, on-screen x, y, w, h}
    private int[] titlePos = new int[] { 0, 0, 256, 256, 0, 50, 187, 145 };

    private int[] progressPos = new int[] { 0, 0, 194, 24, 0, -50, 194, 16 };
    private int[] progressPosAnimated = new int[] { 0, 24, 194, 24, 0, -50, 194, 16 };
    private int[] progressTextPos = new int[] { 0, -30 };
    private int[] progressPercentagePos = new int[] { 0, -40 };

    private int[] secondaryProgressPos = new int[] { 0, 0, 194, 24, 0, -83, 188, 12 };
    private int[] secondaryProgressPosAnimated = new int[] { 0, 24, 194, 24, 0, -83, 188, 12 };
    private int[] secondaryProgressTextPos = new int[] { 0, -65 };
    private int[] secondaryProgressPercentagePos = new int[] { 0, -75 };

    private int[] memoryPos = new int[] { 0, 0, 194, 24, 0, 48, 194, 16 };
    private int[] memoryPosAnimated = new int[] { 0, 24, 194, 24, 0, 48, 194, 16 };

    private int[] tipsTextPos = new int[] { 0, 5 };
    private String baseTipsTextPos = "BOTTOM_CENTER";
    private boolean tipsEnabled = true;
    private String[] randomTips;
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

    public static volatile boolean blending = false;
    public static volatile boolean blendingJustSet = false;
    public static volatile float blendAlpha = 1F;
    public static volatile long blendStartMillis = 0;
    private static String newBlendImage = "none";

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

    private volatile String currentText = "";
    private volatile float currentPercent = 0;
    private volatile String currentSubText = null;
    private volatile float currentSubPercent = Float.NaN;

    private boolean experimental = false;

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
            Double.parseDouble(strNum);
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
        String res = "{";
        for (int i = 0; i < backgrounds.length; i++) {
            res += backgrounds[i];
            if (i < backgrounds.length - 1) {
                res += ", ";
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

        Random rand = new Random();
        String res = randomBackgroundArray[rand.nextInt(randomBackgroundArray.length)];

        if (randomBackgroundArray.length == alreadyUsedBGs.size()) {
            alreadyUsedBGs.clear();
        }

        while (res.equals(currentBG) || alreadyUsedBGs.contains(res)) {
            res = randomBackgroundArray[rand.nextInt(randomBackgroundArray.length)];
        }

        alreadyUsedBGs.add(res);
        return res;
    }

    public String randomTooltip(String currentTooltip) {
        if (randomTips.length == 1) {
            return randomTips[0];
        }

        Random rand = new Random();
        String res = randomTips[rand.nextInt(randomTips.length)];

        if (randomTips.length == alreadyUsedTooltips.size()) {
            alreadyUsedTooltips.clear();
        }

        while (res.equals(currentTooltip) || alreadyUsedTooltips.contains(res)) {
            res = randomTips[rand.nextInt(randomTips.length)];
        }

        alreadyUsedTooltips.add(res);
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

        String comment4 = "What sound to play when loading is complete. Default is the level up sound (" + defaultSound
                + ")";
        sound = cfg.getString("sound", "general", defaultSound, comment4);

        comment4 = "What font texture to use? Special Cases:" + n
                + " - If you use the Russian mod \"Client Fixer\" then change this to \"textures/font/ascii_fat.png\""
                + n
                + "Note: if a resourcepack adds a font, it will be used by BLS.";
        fontTexture = cfg.getString("font", "general", defaultFontTexture, comment4);

        String threadedRenderingComment = "Render the loading screen on a separate thread using a shared OpenGL context."
                + n
                + "Disable this on platforms without shared context support, such as Android."
                + n
                + "When disabled, animations update only when loading progress changes.";
        threadedRendering = cfg.getBoolean("threadedRendering", "general", threadedRendering, threadedRenderingComment);

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

        String comment8 = "Path to main loading bar resource";
        progress = cfg.getString("mainProgressBar", "layout", progress, comment8);

        String comment9 = "Main loading bar position";
        progressPos = stringToIntArray(
                cfg.getString("mainProgressBarPos", "layout", intArrayToString(progressPos), comment9));

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

        String comment12 = "Main loading bar text position. The four values are for position.";
        progressTextPos = stringToIntArray(
                cfg.getString("mainProgressBarTextPos", "layout", intArrayToString(progressTextPos), comment12));

        String comment13 = "Main loading bar percentage position";
        progressPercentagePos = stringToIntArray(
                cfg.getString(
                        "mainProgressBarPercentagePos",
                        "layout",
                        intArrayToString(progressPercentagePos),
                        comment13));

        String comment14 = "Secondary loading bar position";
        secondaryProgressPos = stringToIntArray(
                cfg.getString("secondaryProgressBarPos", "layout", intArrayToString(secondaryProgressPos), comment14));

        String comment15 = "Secondary animated loading bar position";
        secondaryProgressPosAnimated = stringToIntArray(
                cfg.getString(
                        "secondaryProgressBarPosAnimated",
                        "layout",
                        intArrayToString(secondaryProgressPosAnimated),
                        comment15));

        String comment16 = "Secondary loading bar text position";
        secondaryProgressTextPos = stringToIntArray(
                cfg.getString(
                        "secondaryProgressBarTextPos",
                        "layout",
                        intArrayToString(secondaryProgressTextPos),
                        comment16));

        String comment17 = "Secondary loading bar percentage position";
        secondaryProgressPercentagePos = stringToIntArray(
                cfg.getString(
                        "secondaryProgressBarPercentagePos",
                        "layout",
                        intArrayToString(secondaryProgressPercentagePos),
                        comment17));

        String comment39 = "Color of the dynamic loading bars (use ffffff (white) if you don't want to color them)";
        loadingBarsColor = cfg.getString("loadingBarsColor", "layout", loadingBarsColor, comment39);

        String comment40 = "Transparency of the dynamic loading bars";
        loadingBarsAlpha = cfg.getFloat("loadingBarsAlpha", "layout", loadingBarsAlpha, 0, 1, comment40);

        String comment20 = "Whether the text should be rendered with a shadow. Recommended, unless the background is really dark";
        textShadow = cfg.getBoolean("textShadow", "layout", textShadow, comment20);

        String comment21 = "Color of text in hexadecimal format";
        textColor = cfg.getString("textColor", "layout", textColor, comment21);

        String comment22 = "Whether display a random background from the random backgrounds list";
        randomBackgrounds = cfg.getBoolean("randomBackgrounds", "layout", randomBackgrounds, comment22);

        String comment23 = "List of paths to backgrounds that will be used if randomBackgrounds is true." + n
                + "The paths must be separated by commas.";
        randomBackgroundArray = parseBackgroundCFGListToArray(
                cfg.getString(
                        "backgroundList",
                        "layout",
                        parseBackgroundArraytoCFGList(randomBackgroundArray),
                        comment23));

        String comment24 = "Whether backgrounds should change randomly during loading. They are taken from the random background list";
        blendingEnabled = cfg.getBoolean("backgroundChanging", "changing background", blendingEnabled, comment24);

        String comment25 = "Time in milliseconds between each image change (smooth blend).";
        blendTimeMillis = cfg
                .getFloat("blendTimeMilliseconds", "changing background", blendTimeMillis, 0, 30_000, comment25);

        String comment26 = "How many seconds between background changes";
        changeFrequency = cfg.getInt("changeFrequency", "changing background", changeFrequency, 1, 9000, comment26);

        String comment28 = "No, don't touch that!";
        shouldGLClear = cfg.getBoolean("shouldGLClear", "changing background", shouldGLClear, comment28);

        String comment29 = "If you want to save a maximum of time on your loading time but don't want to face a black screen, try this.";
        salt = cfg.getBoolean("salt", "skepticism", salt, comment29);

        String comment30 = "Set to true if you want to load images from an imgur gallery and use them as backgrounds.";
        useImgur = cfg.getBoolean("useImgur", "imgur", useImgur, comment30);

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
            lbRGB[0] = (float) (Color.decode("#" + loadingBarsColor).getRed() & 255) / 255.0f;
            lbRGB[1] = (float) (Color.decode("#" + loadingBarsColor).getGreen() & 255) / 255.0f;
            lbRGB[2] = (float) (Color.decode("#" + loadingBarsColor).getBlue() & 255) / 255.0f;
        } catch (Exception e) {
            lbRGB[0] = 1;
            lbRGB[1] = 0.5176471f;
            lbRGB[2] = 0;
            BetterLoadingScreen.log.warn("Invalid loading bar color, setting default");
        }

        if (salt) {
            blendingEnabled = false;
        }

        if (!preview) {
            if (!ProgressDisplayer.coreModLocation.isDirectory()) {
                myPack = new FMLFileResourcePack(ProgressDisplayer.modContainer);
            } else {
                myPack = new FMLFolderResourcePack(ProgressDisplayer.modContainer);
            }
            getOnlyList().add(myPack);
            mc.refreshResources();
        }

        handleTips();

        if (randomBackgrounds && !salt) {
            Random rand = new Random();
            background = randomBackgroundArray[rand.nextInt(randomBackgroundArray.length)];

            /// timer
            if (!scheduledBackgroundExecSet) {
                scheduledBackgroundExecSet = true;
                backgroundExec = Executors.newSingleThreadScheduledExecutor();
                backgroundExec.scheduleAtFixedRate(new Runnable() {

                    @Override
                    public void run() {
                        if (!blending) {
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
    }

    @Override
    public void displayProgress(String text, float percent) {
        displayProgress(text, percent, null, Float.NaN);
    }

    @Override
    public void displayProgress(String text, float percent, String subText, float subPercent) {
        if (!threadedRendering) {
            renderProgress(text, percent, subText, subPercent);
            mc.func_147120_f();
            return;
        }

        currentText = text;
        currentPercent = percent;
        currentSubText = subText;
        currentSubPercent = subPercent;

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
                        renderProgress(currentText, currentPercent, currentSubText, currentSubPercent);

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

    private void renderProgress(String text, float percent, String subText, float subPercent) {
        resetGlState();
        try {
            displayProgressInWorkerThread(text, percent, subText, subPercent);
        } catch (Exception e) {
            BetterLoadingScreen.log.warn("BLS splash error: ", e);
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

    public void displayProgressInWorkerThread(String text, float percent) {
        displayProgressInWorkerThread(text, percent, null, Float.NaN);
    }

    public void displayProgressInWorkerThread(String text, float percent, String subText, float subPercent) {
        if (salt) {
            displaySaltProgress(percent);
            drawMemoryUsage();
            return;
        }

        List<ImageRender> renderList = new ArrayList<>();

        ImageRender backgroundRender = createBackgroundRender();
        ImageRender titleRender = createTitleRender();

        renderList.add(backgroundRender);
        renderList.add(titleRender);

        ImageRender primaryTextRender = createStatusRender(progressTextPos, "Main progress text");
        ImageRender primaryPercentageRender = createPercentageRender(progressPercentagePos, "Main progress percentage");
        ImageRender primaryBarRender = createBarRender(progress, progressPos, EType.STATIC, "Main progress bar");
        ImageRender primaryAnimatedBarRender = createBarRender(
                progress,
                progressPosAnimated,
                EType.DYNAMIC_PERCENTAGE,
                "Main progress fill");

        renderList.add(primaryTextRender);
        renderList.add(primaryPercentageRender);
        renderList.add(primaryBarRender);
        renderList.add(primaryAnimatedBarRender);

        boolean hasSubProgress = subText != null && !subText.isEmpty();
        boolean subProgressDeterminate = hasSubProgress && !Float.isNaN(subPercent);
        ImageRender secondaryTextRender = null;
        ImageRender secondaryPercentageRender = null;
        ImageRender secondaryBarRender = null;
        ImageRender secondaryAnimatedBarRender = null;

        if (hasSubProgress) {
            secondaryTextRender = createStatusRender(secondaryProgressTextPos, "Secondary progress text");
            renderList.add(secondaryTextRender);

            if (subProgressDeterminate) {
                secondaryPercentageRender = createPercentageRender(
                        secondaryProgressPercentagePos,
                        "Secondary progress percentage");
                secondaryBarRender = createBarRender(
                        progress,
                        secondaryProgressPos,
                        EType.STATIC,
                        "Secondary progress bar");
                secondaryAnimatedBarRender = createBarRender(
                        progress,
                        secondaryProgressPosAnimated,
                        EType.DYNAMIC_PERCENTAGE,
                        "Secondary progress fill");

                renderList.add(secondaryPercentageRender);
                renderList.add(secondaryBarRender);
                renderList.add(secondaryAnimatedBarRender);
            }
        }

        ImageRender tipsRender = null;
        if (tipsEnabled) {
            tipsRender = new ImageRender(
                    fontTexture,
                    EPosition.valueOf(baseTipsTextPos),
                    EType.TIPS_TEXT,
                    null,
                    new Area(tipsTextPos[0], tipsTextPos[1], 0, 0),
                    tipsColor,
                    tip,
                    "Tips");
            renderList.add(tipsRender);
        }

        ImageRender clearRender = new ImageRender(
                null,
                null,
                EType.CLEAR_COLOUR,
                null,
                null,
                "ffffff",
                null,
                "Clear colour");
        renderList.add(clearRender);

        images = renderList.toArray(new ImageRender[0]);

        resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        preDisplayScreen();

        drawImageRender(backgroundRender, null, 0);
        drawImageRender(titleRender, null, 0);

        drawImageRender(primaryBarRender, null, percent);
        drawImageRender(primaryAnimatedBarRender, null, percent);
        drawImageRender(primaryTextRender, text, percent);
        drawImageRender(primaryPercentageRender, null, percent);

        if (hasSubProgress) {
            drawImageRender(secondaryTextRender, subText, subPercent);
            if (subProgressDeterminate) {
                drawImageRender(secondaryBarRender, null, subPercent);
                drawImageRender(secondaryAnimatedBarRender, null, subPercent);
                drawImageRender(secondaryPercentageRender, null, subPercent);
            }
        }

        if (tipsRender != null) {
            drawImageRender(tipsRender, null, 0);
        }
        drawImageRender(clearRender, null, 0);

        drawMemoryUsage();
    }

    private void displaySaltProgress(float percent) {
        shouldGLClear = false;
        textShadow = false;
        textColor = "000000";

        List<ImageRender> renderList = new ArrayList<>();
        if (!saltBGhasBeenRendered) {
            ImageRender backgroundRender = new ImageRender(
                    "betterloadingscreen:textures/salt.png",
                    EPosition.TOP_LEFT,
                    EType.STATIC,
                    new Area(0, 0, 256, 256),
                    new Area(0, 0, 0, 0));
            ImageRender textRender = new ImageRender(
                    fontTexture,
                    EPosition.BOTTOM_LEFT,
                    EType.DYNAMIC_TEXT_STATUS,
                    null,
                    new Area(10, 10, 0, 0),
                    "000000",
                    null,
                    "Salt progress");

            renderList.add(backgroundRender);
            renderList.add(textRender);
            images = renderList.toArray(new ImageRender[0]);

            resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            preDisplayScreen();

            drawImageRender(backgroundRender, null, 0);
            drawImageRender(textRender, "Minecraft is loading, please wait...", percent);
        } else {
            images = new ImageRender[0];
        }
    }

    private ImageRender createBackgroundRender() {
        if (!background.equals("")) {
            return new ImageRender(
                    background,
                    EPosition.TOP_LEFT,
                    EType.STATIC_BLENDED,
                    new Area(0, 0, 256, 256),
                    new Area(0, 0, 0, 0),
                    "ffffff",
                    null,
                    "Background");
        }

        return new ImageRender(
                "betterloadingscreen:textures/transparent.png",
                EPosition.TOP_LEFT,
                EType.STATIC,
                new Area(0, 0, 256, 256),
                new Area(0, 0, 10, 10),
                "ffffff",
                null,
                "Background");
    }

    private ImageRender createTitleRender() {
        if (!title.equals("")) {
            return new ImageRender(
                    title,
                    EPosition.CENTER,
                    EType.STATIC,
                    new Area(titlePos[0], titlePos[1], titlePos[2], titlePos[3]),
                    new Area(titlePos[4], titlePos[5], titlePos[6], titlePos[7]),
                    "ffffff",
                    null,
                    "Logo");
        }

        return new ImageRender(
                "betterloadingscreen:textures/transparent.png",
                EPosition.TOP_LEFT,
                EType.STATIC,
                new Area(0, 0, 256, 256),
                new Area(0, 0, 10, 10),
                "ffffff",
                null,
                "Logo");
    }

    private ImageRender createStatusRender(int[] position, String comment) {
        return new ImageRender(
                fontTexture,
                EPosition.CENTER,
                EType.DYNAMIC_TEXT_STATUS,
                null,
                new Area(position[0], position[1], 0, 0),
                "ffffff",
                null,
                comment);
    }

    private ImageRender createPercentageRender(int[] position, String comment) {
        return new ImageRender(
                fontTexture,
                EPosition.CENTER,
                EType.DYNAMIC_TEXT_PERCENTAGE,
                null,
                new Area(position[0], position[1], 0, 0),
                "ffffff",
                null,
                comment);
    }

    private ImageRender createBarRender(String resource, int[] position, EType type, String comment) {
        return new ImageRender(
                resource,
                EPosition.CENTER,
                type,
                new Area(position[0], position[1], position[2], position[3]),
                new Area(position[4], position[5], position[6], position[7]),
                "ffffff",
                null,
                comment);
    }

    private void drawMemoryUsage() {
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
                    font.drawStringWithShadow(percentage, startX, startY, intColor);
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

                if (experimental) {
                    int currentX = startX;
                    for (int i = 0; i < text.length(); i++) {
                        double scale = 2;
                        BetterLoadingScreen.log.debug("currentX before scale: " + currentX);
                        GL11.glScaled(scale, scale, scale);
                        BetterLoadingScreen.log.debug("currentX after scale: " + currentX);
                        drawString(
                                font,
                                String.valueOf(text.charAt(i)),
                                (int) (currentX / scale),
                                (int) (startY / scale),
                                0);
                        GL11.glScaled(1, 1, 1);
                        currentX += font.getCharWidth(text.charAt(i));
                    }
                } else if (textShadow) {
                    font.drawStringWithShadow(text, startX, startY, intColor);
                } else {
                    drawString(font, text, startX, startY, intColor);
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
                }
                break;
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

        resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
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
