package alexiil.mods.load;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Deprecated
public class Translation {
    private static Map<String, Translation> translators = new HashMap<String, Translation>();
    private static Translation currentTranslation = null;
    private Map<String, String> translations = new HashMap<String, String>();

    public static String translate(String toTranslate) {
        return translate(toTranslate, toTranslate);
    }

    public static String translate(String toTranslate, String failure) {
        // return I18n.format(toTranslate);
        if (currentTranslation != null) return currentTranslation.translateInternal(toTranslate, failure);
        return failure;
    }

    public static void addTranslations(File modLocation) {
        String lookingFor = "assets/betterloadingscreen/lang/";
        if (modLocation == null) {
            BetterLoadingScreen.log.error("Could not find the translation file!");
            return;
        }
        if (modLocation.isDirectory()) {
            File langFolder = new File(modLocation, lookingFor);
            BetterLoadingScreen.log.debug(langFolder.getAbsolutePath() + ", " + langFolder.isDirectory());
            for (File f : langFolder.listFiles()) {
                if (f != null) BetterLoadingScreen.log.debug(f.getAbsolutePath());
                else BetterLoadingScreen.log.debug("null");
            }
        } else if (modLocation.isFile()) {
            JarFile modJar = null;
            try {
                modJar = new JarFile(modLocation);
                Enumeration<JarEntry> entries = modJar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry je = entries.nextElement();
                    String name = je.getName();
                    if (name.startsWith(lookingFor) && !name.equals(lookingFor)) {
                        try {
                            addTranslation(name.replace(lookingFor, "").replace(".lang", ""),
                                    new BufferedReader(new InputStreamReader(modJar.getInputStream(je), StandardCharsets.UTF_8)));
                        } catch (IOException e) {
                            BetterLoadingScreen.log.error("Had trouble opening " + name);
                        }
                    }
                }
            } catch (IOException e) {
                BetterLoadingScreen.log.error("Could not open file");
            } finally {
                if (modJar != null) try {
                    modJar.close();
                } catch (IOException e) {}
            }
        }

        // Lastly, set the current locale
        File options = new File("./options.txt");
        String language = "en_US";
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(options));
            String line = "";
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if (parts[0].equals("lang")) {
                    language = parts[1];
                }
            }
        } catch (IOException ignored) {} finally {
            if (reader != null) try {
                reader.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (translators.containsKey(language)) currentTranslation = translators.get(language);
        else if (translators.containsKey("en_US")) {
            BetterLoadingScreen.log.info("Failed to load " + language + ", loading en_US instead");
            currentTranslation = translators.get("en_US");
        } else if (!translators.isEmpty()) {
            String name = translators.keySet().iterator().next();
            BetterLoadingScreen.log.warn("Failed to load " + language + ", AND FAILED TO LOAD en_US! One available however is " + name +
                ", using that and keeping quiet...");
            currentTranslation = translators.values().iterator().next();
        } else {
            BetterLoadingScreen.log.error("Failed to load ANY languages! All strings fail now!");
        }
    }

    public static boolean addTranslation(String locale, BufferedReader from) {
        try {
            BetterLoadingScreen.log.info("Adding locale " + locale);
            translators.put(locale, new Translation(from));
        } catch (IOException e) {
            BetterLoadingScreen.log.error("Failed to add" + locale);
        }
        return true;
    }

    private Translation(BufferedReader loadFrom) throws IOException {
        try (BufferedReader reader = loadFrom) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.equals("")){
                    String[] splitter = line.split("=");
                    if (splitter.length != 2) {
                        BetterLoadingScreen.log.warn("Found an invalid line (" + line + ")");
                    } else {
                        translations.put(splitter[0], splitter[1]);
                        BetterLoadingScreen.log.debug("Found a translation " + Arrays.toString(splitter));
                    }
                }
            }
        }
    }

    private String translateInternal(String toTranslate, String failure) {
        if (translations.containsKey(toTranslate)) return translations.get(toTranslate);
        return failure;
    }
}
