package alexiil.mods.load;

import java.io.IOException;

import cpw.mods.fml.common.ProgressManager.ProgressBar;
import cpw.mods.fml.relauncher.FMLLaunchHandler;

public final class FMLProgressTracker {

    public enum State {

        CONSTRUCT("construction", "Construction", "Construction"),
        PRE_INIT("pre_initialization", "Pre Initialization", "PreInitialization"),
        LITE_LOADER_INIT("lite", "LiteLoader", null),
        INIT("initialization", "Initialization", "Initialization"),
        POST_INIT("post_initialization", "Post Initialization", "PostInitialization"),
        LOAD_COMPLETE("completed", "Completed", "LoadComplete"),
        FINAL_LOADING("reloading_resource_packs", "Reloading Resource Packs", null);

        public static final State[] VALUES = values();

        private final String nameKey;
        private final String fallbackName;
        private final String fmlTitle;
        private String translatedName = null;

        State(String nameKey, String fallbackName, String fmlTitle) {
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

        public static State fromFmlTitle(String title) {
            if (title == null) return null;
            for (State state : VALUES) {
                if (title.equals(state.fmlTitle)) return state;
            }
            return null;
        }
    }

    private FMLProgressTracker() {}

    /**
     * Called when FML advances a progress bar to the next mod, before that mod's lifecycle handler is executed.
     */
    public static void onBarStep(ProgressBar bar) {
        State state = State.fromFmlTitle(bar.getTitle());
        if (state == null || !FMLLaunchHandler.side().isClient()) return;

        int steps = Math.max(1, bar.getSteps());
        int completedSteps = Math.max(0, bar.getStep() - 1);
        float stateSize = 1F / (State.VALUES.length - 1);
        float percent = state.ordinal() * stateSize + stateSize * completedSteps / steps;

        String text = state.translate();
        String message = bar.getMessage();
        if (message != null && !message.isEmpty()) {
            text += ": " + Translation.translate("betterloadingscreen.loading", "loading") + " " + message;
        }

        displayProgress(text, percent);
    }

    /**
     * Called when FML finishes processing all mods for the current lifecycle stage.
     */
    public static void onBarPop(ProgressBar bar) {
        State state = State.fromFmlTitle(bar.getTitle());
        if (state == null || !FMLLaunchHandler.side().isClient()) return;

        // Set the final loading text, as this is the last lifecycle state FML gives us
        if (state == State.LOAD_COMPLETE) {
            displayProgress(State.FINAL_LOADING.translate(), 1F);
            return;
        }

        // The next phase may not begin immediately after this bar is popped, so show only the completed stage
        // without the last processed mod name, to avoid misleading users into thinking that mod caused a freeze
        float stateSize = 1F / (State.VALUES.length - 1);
        float percent = (state.ordinal() + 1) * stateSize;
        displayProgress(state.translate(), percent);
    }

    private static void displayProgress(String text, float percent) {
        try {
            ProgressDisplayer.displayProgress(text, percent);
        } catch (IOException e) {
            BetterLoadingScreen.log.error("Failed to update FML loading progress", e);
        }
    }
}
