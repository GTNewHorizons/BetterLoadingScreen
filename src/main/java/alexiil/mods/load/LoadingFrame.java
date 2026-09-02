package alexiil.mods.load;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

public class LoadingFrame extends JFrame {

    private final JLabel primaryLabel;
    private final JProgressBar primaryProgressBar;

    private final JPanel secondaryPanel;
    private final JLabel secondaryLabel;
    private final JProgressBar secondaryProgressBar;

    public static void setSystemLAF() {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Throwable t) {
            BetterLoadingScreen.log.warn("Failed to set system look and feel", t);
        }
    }

    public static LoadingFrame openWindow() {
        try {
            LoadingFrame frame = new LoadingFrame();
            frame.setBounds(getWindowBounds(frame));
            frame.setAlwaysOnTop(true);
            frame.setVisible(true);
            return frame;
        } catch (Exception e) {
            BetterLoadingScreen.log.error("Failed to open loading window", e);
            return null;
        }
    }

    private static Rectangle getWindowBounds(LoadingFrame frame) {
        Dimension size = Toolkit.getDefaultToolkit().getScreenSize();
        Rectangle bounds = frame.getBounds();

        return new Rectangle(
                (size.width - bounds.width) / 2,
                (size.height - bounds.height) / 2,
                bounds.width,
                bounds.height);
    }

    public LoadingFrame() {
        setTitle("Minecraft Loading");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setBounds(100, 100, 450, 120);

        JPanel contentPane = new JPanel(new GridLayout(2, 1, 0, 4));
        contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
        setContentPane(contentPane);

        JPanel primaryPanel = new JPanel(new BorderLayout());
        primaryLabel = new JLabel("Minecraft Forge Starting");
        primaryProgressBar = new JProgressBar(0, 100);
        primaryProgressBar.setStringPainted(true);

        primaryPanel.add(primaryLabel, BorderLayout.NORTH);
        primaryPanel.add(primaryProgressBar, BorderLayout.CENTER);
        contentPane.add(primaryPanel);

        secondaryPanel = new JPanel(new BorderLayout());
        secondaryLabel = new JLabel();
        secondaryProgressBar = new JProgressBar(0, 100);
        secondaryProgressBar.setStringPainted(true);

        secondaryPanel.add(secondaryLabel, BorderLayout.NORTH);
        secondaryPanel.add(secondaryProgressBar, BorderLayout.CENTER);
        secondaryPanel.setVisible(false);
        contentPane.add(secondaryPanel);
    }

    public void setProgress(String text, float percent, String subText, float subPercent) {
        primaryLabel.setText(text);
        primaryProgressBar.setValue((int) (percent * 100F));

        boolean hasSubProgress = subText != null && !subText.isEmpty();
        secondaryPanel.setVisible(hasSubProgress);

        if (hasSubProgress) {
            secondaryLabel.setText(subText);

            boolean indeterminate = Float.isNaN(subPercent);
            secondaryProgressBar.setIndeterminate(indeterminate);

            if (!indeterminate) {
                secondaryProgressBar.setValue((int) (subPercent * 100F));
            }
        }

        revalidate();
        repaint();
    }
}
