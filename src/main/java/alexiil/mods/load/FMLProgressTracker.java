package alexiil.mods.load;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import cpw.mods.fml.common.ProgressManager;
import cpw.mods.fml.common.ProgressManager.ProgressBar;
import cpw.mods.fml.relauncher.FMLLaunchHandler;

public final class FMLProgressTracker {

    public enum Stage {

        CONSTRUCT("construction", "Construction", "Construction"),
        PRE_INIT("pre_initialization", "Pre Initialization", "PreInitialization"),
        LITE_LOADER_INIT("lite", "LiteLoader", null),
        INIT("initialization", "Initialization", "Initialization"),
        POST_INIT("post_initialization", "Post Initialization", "PostInitialization"),
        LOAD_COMPLETE("completed", "Completed", "LoadComplete"),
        FINAL_LOADING("reloading_resource_packs", "Reloading Resource Packs", null);

        public static final Stage[] VALUES = values();
        public static final float PROGRESS_SPAN = 1F / (VALUES.length - 1);

        private final String nameKey;
        private final String fallbackName;
        private final String fmlTitle;
        private String translatedName = null;

        Stage(String nameKey, String fallbackName, String fmlTitle) {
            this.nameKey = nameKey;
            this.fallbackName = fallbackName;
            this.fmlTitle = fmlTitle;
        }

        public String translate() {
            if (translatedName == null) {
                translatedName = Translation.translate("betterloadingscreen.state." + nameKey, fallbackName);
            }
            return translatedName;
        }

        public static Stage fromProgressBar(ProgressBar bar) {
            if (activeBars.size() <= FML_LIFECYCLE_BAR_INDEX) return null;
            if (activeBars.get(FML_LIFECYCLE_BAR_INDEX) != bar) return null;
            for (Stage stage : VALUES) {
                if (bar.getTitle().equals(stage.fmlTitle)) return stage;
            }
            return null;
        }
    }

    // The first bar is always "Loading"
    private static final int FML_ROOT_BAR_INDEX = 0;
    // The second bar is normally the lifecycle stage, but it can be popped before the next stage begins.
    // A mod may report another bar in between, but it must be popped before the next lifecycle stage is pushed.
    // Otherwise, even FML's own progress bar renderer would be broken
    private static final int FML_LIFECYCLE_BAR_INDEX = 1;
    private static volatile List<ProgressBar> activeBars = Collections.emptyList();

    private FMLProgressTracker() {}

    /**
     * Called after FML adds a progress bar.
     */
    public static void onBarPush(ProgressBar bar) {
        if (!FMLLaunchHandler.side().isClient()) return;

        refreshActiveBars();
        displaySubProgress();
    }

    /**
     * Called after FML advances a progress bar to its next step.
     */
    public static void onBarStep(ProgressBar bar) {
        if (!FMLLaunchHandler.side().isClient()) return;

        Stage stage = Stage.fromProgressBar(bar);
        if (stage == null) {
            displaySubProgress();
            return;
        }

        int steps = Math.max(1, bar.getSteps());
        int completedSteps = Math.max(0, bar.getStep() - 1);
        float percent = (stage.ordinal() + (float) completedSteps / steps) * Stage.PROGRESS_SPAN;

        String text = stage.translate();
        String message = bar.getMessage();
        if (message != null && !message.isEmpty()) {
            text += ": " + Translation.translate("betterloadingscreen.loading", "loading") + " " + message;
        }

        displayProgress(text, percent);
    }

    /**
     * Called after FML removes a completed progress bar.
     */
    public static void onBarPop(ProgressBar bar) {
        if (!FMLLaunchHandler.side().isClient()) return;

        Stage poppedStage = Stage.fromProgressBar(bar);
        refreshActiveBars();

        if (poppedStage == null) {
            displaySubProgress();
            return;
        }

        if (poppedStage == Stage.LOAD_COMPLETE) {
            displayProgress(Stage.FINAL_LOADING.translate(), 1F);
        } else {
            // The next phase may not begin immediately after this bar is popped, so show only the completed stage
            // without the last processed mod name, to avoid misleading users into thinking that mod caused a freeze.
            float percent = (poppedStage.ordinal() + 1) * Stage.PROGRESS_SPAN;
            displayProgress(poppedStage.translate(), percent);
        }
    }

    private static void refreshActiveBars() {
        List<ProgressBar> bars = new ArrayList<>();
        Iterator<ProgressBar> iterator = ProgressManager.barIterator();

        while (iterator.hasNext()) {
            bars.add(iterator.next());
        }

        activeBars = bars;
    }

    private static void displayProgress(String text, float percent) {
        SubProgress subProgress = getSubProgress();
        try {
            if (subProgress == null) {
                ProgressDisplayer.displayProgress(text, percent, null, Float.NaN);
            } else {
                ProgressDisplayer.displayProgress(text, percent, subProgress.text, subProgress.percent);
            }
        } catch (IOException e) {
            BetterLoadingScreen.log.error("Failed to update FML loading progress", e);
        }
    }

    private static void displaySubProgress() {
        SubProgress subProgress = getSubProgress();
        try {
            if (subProgress == null) {
                ProgressDisplayer.clearSubProgress();
            } else {
                ProgressDisplayer.displaySubProgress(subProgress.text, subProgress.percent);
            }
        } catch (IOException e) {
            BetterLoadingScreen.log.error("Failed to update FML sub-progress", e);
        }
    }

    /**
     * Uses the first and the last active bar below the primary bar so we don't lose as much context.
     */
    private static SubProgress getSubProgress() {
        final List<ProgressBar> bars = activeBars; // volatile snapshot
        ProgressBar first = null;
        ProgressBar last = null;

        for (int i = FML_ROOT_BAR_INDEX + 1; i < bars.size(); i++) {
            ProgressBar bar = bars.get(i);
            if (Stage.fromProgressBar(bar) != null) continue;

            String title = bar.getTitle();
            if (title == null || title.isEmpty()) continue;

            if (first == null) first = bar;
            last = bar;
        }
        if (last == null) return null;

        StringBuilder text = new StringBuilder(first.getTitle());
        if (last != first) {
            text.append(" - ").append(last.getTitle());
        }

        String message = last.getMessage();
        if (message != null && !message.isEmpty()) {
            text.append(": ").append(message);
        }

        float percent = last.getSteps() > 0 ? (float) last.getStep() / last.getSteps() : Float.NaN;
        return new SubProgress(text.toString(), percent);
    }

    private static final class SubProgress {

        private final String text;
        private final float percent;

        private SubProgress(String text, float percent) {
            this.text = text;
            this.percent = percent;
        }
    }
}
