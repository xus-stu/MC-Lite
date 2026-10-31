package mclite;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListCellRenderer;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.Path;

public final class Ui {
    private Ui() {
    }

    public static Graphics2D prepare(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        boolean windows = System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).contains("win");
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                windows ? RenderingHints.VALUE_RENDER_SPEED : RenderingHints.VALUE_RENDER_QUALITY);
        return g2;
    }

    public static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        return label;
    }

    public static JLabel title(String text) {
        return label(text, Theme.H1, Theme.TEXT);
    }

    public static JLabel muted(String text) {
        return label(text, Theme.SMALL, Theme.MUTED);
    }

    public static JTextArea note(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(Theme.SMALL);
        area.setForeground(Theme.DIM);
        area.setBorder(new EmptyBorder(0, 0, 0, 0));
        return area;
    }

    public static AccentButton button(String text, AccentButton.Style style) {
        return new AccentButton(text, style, Theme.BODY_BOLD, new Insets(7, 12, 7, 12));
    }

    public static AccentButton smallButton(String text, AccentButton.Style style) {
        return new AccentButton(text, style, Theme.SMALL, new Insets(4, 9, 4, 9));
    }

    public static PlaceholderField field(String placeholder) {
        return new PlaceholderField(placeholder);
    }

    public static PlaceholderField field(String text, String placeholder) {
        PlaceholderField field = new PlaceholderField(placeholder);
        field.setText(text);
        return field;
    }

    public static JScrollPane scroll(Component view) {
        JScrollPane pane = new JScrollPane(view);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pane.getVerticalScrollBar().setUnitIncrement(22);
        pane.getVerticalScrollBar().setOpaque(false);
        pane.getVerticalScrollBar().setUI(new ThinScrollBarUI());
        pane.getVerticalScrollBar().setPreferredSize(new Dimension(11, 0));
        return pane;
    }

    public static JScrollPane scrollDark(Component view) {
        JScrollPane pane = new JScrollPane(view);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setOpaque(true);
        pane.setBackground(Theme.BG);
        pane.getViewport().setOpaque(true);
        pane.getViewport().setBackground(Theme.BG);
        pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pane.getVerticalScrollBar().setUnitIncrement(24);
        pane.getVerticalScrollBar().setOpaque(false);
        pane.getVerticalScrollBar().setUI(new ThinScrollBarUI());
        pane.getVerticalScrollBar().setPreferredSize(new Dimension(11, 0));
        return pane;
    }

    public static final class ValueSlider extends JComponent {
        private final int min;
        private final int max;
        private final int step;
        private final String suffix;
        private int value;
        private java.util.function.IntConsumer listener;
        private boolean dragging;

        public ValueSlider(int min, int max, int step, int value, String suffix) {
            this.min = min;
            this.max = max;
            this.step = Math.max(1, step);
            this.suffix = suffix == null ? "" : suffix;
            this.value = snap(value);
            setPreferredSize(new Dimension(250, 30));
            setMinimumSize(new Dimension(160, 30));
            setMaximumSize(new Dimension(320, 30));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(min + " – " + max);
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    dragging = true;
                    updateFromMouse(e.getX());
                }

                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    dragging = false;
                }
            });
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                @Override
                public void mouseDragged(java.awt.event.MouseEvent e) {
                    updateFromMouse(e.getX());
                }
            });
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            int next = snap(value);
            if (next != this.value) {
                this.value = next;
                repaint();
                if (listener != null) {
                    listener.accept(next);
                }
            }
        }

        public void addChangeListener(java.util.function.IntConsumer listener) {
            this.listener = listener;
        }

        private int snap(int raw) {
            int clamped = Math.max(min, Math.min(max, raw));
            int steps = Math.round((clamped - min) / (float) step);
            return Math.max(min, Math.min(max, min + steps * step));
        }

        private void updateFromMouse(int x) {
            int trackWidth = Math.max(20, getWidth() - 76);
            double ratio = (x - 6) / (double) trackWidth;
            ratio = Math.max(0, Math.min(1, ratio));
            setValue((int) Math.round(min + ratio * (max - min)));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prepare(g);
            int width = getWidth();
            int height = getHeight();
            int trackWidth = Math.max(20, width - 76);
            int y = height / 2 - 2;
            g2.setColor(Theme.CARD_ALT);
            g2.fillRoundRect(6, y, trackWidth, 5, 5, 5);
            double ratio = (value - min) / (double) (max - min);
            int filled = (int) Math.round(trackWidth * ratio);
            g2.setColor(isEnabled() ? Theme.ACCENT : Theme.DIM);
            if (filled > 0) {
                g2.fillRoundRect(6, y, Math.max(5, filled), 5, 5, 5);
            }
            int knobX = 6 + filled;
            g2.setColor(isEnabled() ? Theme.ACCENT : Theme.DIM);
            g2.fillOval(knobX - 6, height / 2 - 6, 12, 12);
            g2.setColor(Theme.BG);
            g2.fillOval(knobX - 2, height / 2 - 2, 4, 4);
            g2.setFont(Theme.MONO);
            String text = value + suffix;
            FontMetrics metrics = g2.getFontMetrics();
            g2.setColor(isEnabled() ? Theme.MUTED : Theme.DIM);
            g2.drawString(text, width - metrics.stringWidth(text) - 3,
                    (height - metrics.getHeight()) / 2 + metrics.getAscent());
            g2.dispose();
        }
    }

    public static JSpinner memorySpinner(int value) {
        return numberSpinner(value, 512, 32768, 512, 78);
    }

    public static JSpinner numberSpinner(int value, int min, int max, int step, int width) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, min, max, step));
        spinner.setFont(Theme.BODY);
        spinner.setBackground(Theme.FIELD);
        spinner.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0");
        JTextField text = editor.getTextField();
        text.setFont(Theme.BODY);
        text.setBackground(Theme.FIELD);
        text.setForeground(Theme.TEXT);
        text.setCaretColor(Theme.ACCENT);
        text.setBorder(new EmptyBorder(6, 8, 6, 8));
        spinner.setEditor(editor);
        spinner.setPreferredSize(new Dimension(width, 30));
        spinner.setMaximumSize(new Dimension(width, 30));
        return spinner;
    }

    public static Path chooseDirectory(Component parent, String title, Path initial) {
        return showFileDialog(parent, title, initial, true, null);
    }

    public static Path chooseFile(Component parent, String title, Path initial,
                                  String... extensions) {
        return showFileDialog(parent, title, initial, false, extensions);
    }

    private static Path showFileDialog(Component parent, String title, Path initial,
                                       boolean directory, String[] extensions) {
        String dialogTitle = title == null ? "选择" : title;
        FileDialog picker;
        if (parent instanceof Frame frame) {
            picker = new FileDialog(frame, dialogTitle, FileDialog.LOAD);
        } else if (parent instanceof Dialog dialog) {
            picker = new FileDialog(dialog, dialogTitle, FileDialog.LOAD);
        } else {
            picker = new FileDialog((Frame) null, dialogTitle, FileDialog.LOAD);
        }
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        if (os.contains("mac")) {
            System.setProperty("apple.awt.fileDialogForDirectories",
                    Boolean.toString(directory));
        }
        Path location = initial;
        if (location != null && !java.nio.file.Files.isDirectory(location)) {
            location = location.getParent();
        }
        if (location != null) {
            picker.setDirectory(location.toAbsolutePath().normalize().toString());
        }
        if (!directory && extensions != null && extensions.length > 0) {
            picker.setFilenameFilter((dir, name) -> {
                String lower = name.toLowerCase(java.util.Locale.ROOT);
                for (String extension : extensions) {
                    if (extension == null || extension.isBlank()) {
                        continue;
                    }
                    String suffix = extension.startsWith(".")
                            ? extension.toLowerCase(java.util.Locale.ROOT)
                            : "." + extension.toLowerCase(java.util.Locale.ROOT);
                    if (lower.endsWith(suffix)) {
                        return true;
                    }
                }
                return false;
            });
        }
        picker.setMultipleMode(false);
        picker.setVisible(true);
        String name = picker.getFile();
        String dir = picker.getDirectory();
        picker.dispose();
        if (name == null) {
            return null;
        }
        if (name.isBlank()) {
            return directory && dir != null && !dir.isBlank()
                    ? Path.of(dir).toAbsolutePath().normalize() : null;
        }
        Path selected = dir == null || dir.isBlank()
                ? Path.of(name) : Path.of(dir, name);
        return selected.toAbsolutePath().normalize();
    }

    public static final class FadePanel extends JPanel {
        private float alpha = 1f;
        private int offset;
        private long started;
        private Timer timer;

        public FadePanel(java.awt.LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        public void fadeIn() {
            if (!isShowing()) {
                alpha = 1f;
                offset = 0;
                repaint();
                return;
            }
            alpha = 0f;
            offset = 10;
            started = System.currentTimeMillis();
            if (timer == null) {
                timer = new Timer(16, e -> {
                    float elapsed = (System.currentTimeMillis() - started) / 260f;
                    float t = Math.min(1f, elapsed);
                    float eased = 1f - (1f - t) * (1f - t) * (1f - t);
                    alpha = eased;
                    offset = Math.round((1f - eased) * 10f);
                    repaint();
                    if (t >= 1f) {
                        ((Timer) e.getSource()).stop();
                        offset = 0;
                    }
                });
            }
            timer.restart();
        }

        @Override
        protected void paintChildren(Graphics g) {
            if (alpha >= 0.999f && offset == 0) {
                super.paintChildren(g);
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setComposite(java.awt.AlphaComposite.SrcOver.derive(Math.max(0f, alpha)));
            g2.translate(0, offset);
            super.paintChildren(g2);
            g2.dispose();
        }
    }

    public static String fitText(Graphics2D g, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        FontMetrics metrics = g.getFontMetrics();
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int width = metrics.stringWidth(ellipsis);
        if (width > maxWidth) {
            return "";
        }
        int end = text.length();
        while (end > 0 && width + metrics.stringWidth(text.substring(0, end)) > maxWidth) {
            end--;
        }
        return end == 0 ? "" : text.substring(0, end) + ellipsis;
    }

    public static JComponent hgap(int width) {
        return (JComponent) Box.createHorizontalStrut(width);
    }

    public static JComponent vgap(int height) {
        return (JComponent) Box.createVerticalStrut(height);
    }

    public static JPanel row(int gap) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        if (gap > 0) {
            panel.add(Box.createHorizontalStrut(gap));
        }
        return panel;
    }

    public static final class RoundedPanel extends JPanel {
        private final Color fill;
        private final Color border;
        private final int radius;

        public RoundedPanel(Color fill, Color border, int radius) {
            this.fill = fill;
            this.border = border;
            this.radius = radius;
            setOpaque(false);
        }

        public RoundedPanel() {
            this(Theme.CARD, Theme.BORDER, 10);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prepare(g);
            int width = getWidth();
            int height = getHeight();
            if (fill != null) {
                g2.setColor(fill);
                g2.fill(new RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f,
                        radius, radius));
            }
            if (border != null) {
                g2.setColor(border);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f,
                        radius, radius));
            }
            g2.dispose();
        }
    }

    public static final class AccentButton extends JButton {
        public enum Style {PRIMARY, SECONDARY, GHOST, DANGER, LINK}

        private final Style style;
        private final int arc = 9;
        private float hoverProgress;
        private float hoverFrom;
        private float hoverTo;
        private long hoverStarted;
        private Timer hoverTimer;

        public AccentButton(String text, Style style, Font font, Insets padding) {
            super(text);
            this.style = style;
            setFont(font);
            setBorder(new EmptyBorder(padding));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setForeground(style == Style.PRIMARY ? Theme.ACCENT_TEXT : Theme.TEXT);
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    animateHover(1f);
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    animateHover(0f);
                }
            });
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            if (!enabled) {
                hoverProgress = 0f;
                if (hoverTimer != null) {
                    hoverTimer.stop();
                }
            }
        }

        private void animateHover(float target) {
            if (!isEnabled() || Math.abs(hoverProgress - target) < 0.01f) {
                return;
            }
            hoverFrom = hoverProgress;
            hoverTo = target;
            hoverStarted = System.currentTimeMillis();
            if (hoverTimer == null) {
                hoverTimer = new Timer(16, e -> {
                    float elapsed = (System.currentTimeMillis() - hoverStarted) / 150f;
                    float t = Math.min(1f, elapsed);
                    float eased = 1f - (1f - t) * (1f - t);
                    hoverProgress = hoverFrom + (hoverTo - hoverFrom) * eased;
                    repaint();
                    if (t >= 1f) {
                        ((Timer) e.getSource()).stop();
                    }
                });
            }
            hoverTimer.restart();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prepare(g);
            int width = getWidth();
            int height = getHeight();
            boolean pressed = getModel().isPressed();
            float h = isEnabled() ? hoverProgress : 0f;
            Color fill = null;
            Color border = null;
            Color text = Theme.TEXT;
            switch (style) {
                case PRIMARY -> {
                    fill = pressed ? Theme.ACCENT_2 : Theme.mix(Theme.ACCENT, Theme.ACCENT_HOVER, h);
                    border = Theme.ACCENT_2;
                    text = Theme.ACCENT_TEXT;
                }
                case SECONDARY -> {
                    fill = Theme.mix(Theme.CARD_ALT, Theme.HOVER, h);
                    border = Theme.mix(Theme.BORDER, Theme.BORDER_LIGHT, h);
                    text = Theme.TEXT;
                }
                case GHOST -> {
                    fill = h <= 0.01f ? null : Theme.alpha(Theme.BG_4, Math.round(255 * h));
                    text = Theme.mix(Theme.MUTED, Theme.TEXT, h);
                }
                case DANGER -> {
                    fill = h <= 0.01f ? null : Theme.alpha(Theme.DANGER, Math.round(32 * h));
                    border = Theme.alpha(Theme.DANGER, Math.round(60 + 60 * h));
                    text = Theme.DANGER;
                }
                case LINK -> {
                    fill = null;
                    text = isEnabled() ? Theme.mix(Theme.DIM, Theme.ACCENT, h) : Theme.DIM;
                }
            }
            if (!isEnabled()) {
                text = Theme.DIM;
                if (fill != null) {
                    fill = Theme.alpha(fill, 95);
                }
            }
            int inset = pressed ? 1 : 0;
            if (fill != null) {
                g2.setColor(fill);
                g2.fillRoundRect(inset, inset, width - inset * 2, height - inset * 2,
                        arc, arc);
            }
            if (border != null) {
                g2.setColor(border);
                g2.drawRoundRect(inset, inset, width - 1 - inset * 2,
                        height - 1 - inset * 2, arc, arc);
            }
            g2.setFont(getFont());
            FontMetrics metrics = g2.getFontMetrics();
            String value = fitText(g2, getText(), width - 6);
            int textWidth = metrics.stringWidth(value);
            int x = Math.max(3, (width - textWidth) / 2);
            int y = (height - metrics.getHeight()) / 2 + metrics.getAscent();
            if (!value.isEmpty()) {
                g2.setColor(text);
                g2.drawString(value, x, y);
                if (style == Style.LINK && h > 0.35f) {
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawLine(x, y + 2, x + textWidth, y + 2);
                }
            }
            g2.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(Math.max(size.width, 48), Math.max(size.height, 30));
        }
    }

    public static final class Pill extends JLabel {
        private final Color fill;
        private final Color border;
        private final Color textColor;

        public Pill(String text, Color fill, Color textColor, Color border) {
            super(text);
            this.fill = fill;
            this.textColor = textColor;
            this.border = border;
            setFont(Theme.MONO);
            setForeground(textColor);
            setBorder(new EmptyBorder(2, 8, 2, 8));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prepare(g);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            if (border != null) {
                g2.setColor(border);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static final class PlaceholderField extends JTextField {
        private final String placeholder;

        public PlaceholderField(String placeholder) {
            this.placeholder = placeholder;
            setOpaque(false);
            setFont(Theme.BODY);
            setForeground(Theme.TEXT);
            setCaretColor(Theme.ACCENT);
            setSelectionColor(Theme.SELECTED);
            setSelectedTextColor(Theme.TEXT);
            setBorder(new EmptyBorder(7, 9, 7, 9));
            setBackground(Theme.FIELD);
            addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(FocusEvent e) {
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prepare(g);
            int width = getWidth();
            int height = getHeight();
            g2.setColor(Theme.CARD_ALT);
            g2.fillRoundRect(0, 0, width, height, 9, 9);
            g2.setColor(hasFocus() ? Theme.ACCENT : Theme.BORDER);
            g2.drawRoundRect(0, 0, width - 1, height - 1, 9, 9);
            g2.dispose();
            super.paintComponent(g);
            if (getText().isEmpty() && !hasFocus() && placeholder != null) {
                Graphics2D hint = prepare(g);
                hint.setFont(getFont());
                hint.setColor(Theme.DIM);
                Insets insets = getInsets();
                FontMetrics metrics = hint.getFontMetrics();
                int y = (height - metrics.getHeight()) / 2 + metrics.getAscent();
                String shown = fitText(hint, placeholder,
                        width - insets.left - insets.right - 6);
                if (!shown.isEmpty()) {
                    hint.drawString(shown, insets.left + 3, y);
                }
                hint.dispose();
            }
        }
    }

    public static final class DarkCombo<E> extends JComboBox<E> {
        public DarkCombo() {
            setFont(Theme.BODY);
            setForeground(Theme.TEXT);
            setBackground(Theme.CARD_ALT);
            setFocusable(false);
            setMaximumRowCount(12);
            setOpaque(false);
            setBorder(new EmptyBorder(0, 0, 0, 0));
            setUI(new BasicComboBoxUI() {
                @Override
                protected JButton createArrowButton() {
                    JButton button = new JButton();
                    button.setPreferredSize(new Dimension(0, 0));
                    button.setMinimumSize(new Dimension(0, 0));
                    button.setMaximumSize(new Dimension(0, 0));
                    button.setBorder(BorderFactory.createEmptyBorder());
                    button.setContentAreaFilled(false);
                    button.setFocusPainted(false);
                    return button;
                }

                @Override
                public void paintCurrentValueBackground(Graphics g, java.awt.Rectangle bounds,
                                                        boolean hasFocus) {

                }
            });
            setRenderer(new ListCellRenderer<E>() {
                private final JLabel label = new JLabel();

                @Override
                public Component getListCellRendererComponent(JList<? extends E> list, E value,
                                                              int index, boolean selected,
                                                              boolean cellHasFocus) {
                    label.setText(value == null ? "" : value.toString());
                    label.setFont(Theme.SMALL);
                    label.setOpaque(index >= 0);
                    label.setBorder(new EmptyBorder(index < 0 ? 5 : 4,
                            index < 0 ? 12 : 8, index < 0 ? 5 : 4, index < 0 ? 12 : 8));
                    label.setBackground(selected ? Theme.SELECTED : Theme.CARD_ALT);
                    label.setForeground(selected ? Theme.ACCENT : Theme.TEXT);
                    return label;
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prepare(g);
            g2.setColor(Theme.CARD_ALT);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.setColor(Theme.BORDER);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static final class NavTab extends JToggleButton {
        private float selectedProgress;
        private float selectFrom;
        private float selectTo;
        private long selectStarted;
        private Timer selectTimer;

        public NavTab(String text) {
            super(text);
            setFont(Theme.SMALL);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorder(new EmptyBorder(0, 14, 0, 14));
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(92, 40));
            setMaximumSize(new Dimension(120, 40));
            getModel().addChangeListener(e -> animateSelection(180f));
        }

        private void animateSelection(float durationMs) {
            float target = isSelected() ? 1f : 0f;
            if (Math.abs(selectedProgress - target) < 0.001f) {
                return;
            }
            selectFrom = selectedProgress;
            selectTo = target;
            selectStarted = System.currentTimeMillis();
            if (selectTimer == null) {
                selectTimer = new Timer(16, e -> {
                    float elapsed = (System.currentTimeMillis() - selectStarted) / durationMs;
                    float t = Math.min(1f, elapsed);
                    float eased = 1f - (1f - t) * (1f - t);
                    selectedProgress = selectFrom + (selectTo - selectFrom) * eased;
                    repaint();
                    if (t >= 1f) {
                        ((Timer) e.getSource()).stop();
                    }
                });
            }
            selectTimer.restart();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prepare(g);
            int width = getWidth();
            int height = getHeight();
            if (selectedProgress > 0.01f) {
                g2.setColor(Theme.alpha(Theme.ACCENT, Math.round(18 * selectedProgress)));
                g2.fillRoundRect(8, 6, width - 16, height - 12, 9, 9);
                int underline = Math.round((width - 32) * selectedProgress);
                g2.setColor(Theme.ACCENT);
                g2.fillRoundRect((width - underline) / 2, height - 3, underline, 2, 2, 2);
            }
            if (getModel().isRollover() && selectedProgress < 0.99f) {
                g2.setColor(Theme.CARD_ALT);
                g2.fillRoundRect(8, 6, width - 16, height - 12, 9, 9);
            }
            g2.setFont(getFont());
            FontMetrics metrics = g2.getFontMetrics();
            String text = fitText(g2, getText(), width - 6);
            g2.setColor(Theme.mix(Theme.MUTED, Theme.TEXT, selectedProgress));
            if (!text.isEmpty()) {
                g2.drawString(text, Math.max(3, (width - metrics.stringWidth(text)) / 2),
                        (height - metrics.getHeight()) / 2 + metrics.getAscent());
            }
            g2.dispose();
        }
    }

    public static final class SegmentButton extends JToggleButton {
        private float selectedProgress;
        private float selectFrom;
        private float selectTo;
        private long selectStarted;
        private Timer selectTimer;

        public SegmentButton(String text) {
            super(text);
            setFont(Theme.SMALL);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorder(new EmptyBorder(5, 11, 5, 11));
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            getModel().addChangeListener(e -> animateSelection());
        }

        private void animateSelection() {
            float target = isSelected() ? 1f : 0f;
            if (Math.abs(selectedProgress - target) < 0.001f) {
                return;
            }
            selectFrom = selectedProgress;
            selectTo = target;
            selectStarted = System.currentTimeMillis();
            if (selectTimer == null) {
                selectTimer = new Timer(16, e -> {
                    float elapsed = (System.currentTimeMillis() - selectStarted) / 160f;
                    float t = Math.min(1f, elapsed);
                    float eased = 1f - (1f - t) * (1f - t);
                    selectedProgress = selectFrom + (selectTo - selectFrom) * eased;
                    repaint();
                    if (t >= 1f) {
                        ((Timer) e.getSource()).stop();
                    }
                });
            }
            selectTimer.restart();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prepare(g);
            boolean hover = getModel().isRollover();
            if (selectedProgress > 0.01f) {
                g2.setColor(Theme.alpha(Theme.SEL, Math.round(48 * selectedProgress)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 9, 9);
                g2.setColor(Theme.alpha(Theme.ACCENT, Math.round(120 * selectedProgress)));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 9, 9);
            } else if (hover) {
                g2.setColor(Theme.BG_4);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 9, 9);
            }
            g2.setFont(getFont());
            FontMetrics metrics = g2.getFontMetrics();
            String text = fitText(g2, getText(), getWidth() - 6);
            g2.setColor(Theme.mix(Theme.MUTED, Theme.ACCENT, selectedProgress));
            if (!text.isEmpty()) {
                g2.drawString(text, Math.max(3, (getWidth() - metrics.stringWidth(text)) / 2),
                        (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
            }
            g2.dispose();
        }
    }

    public static final class ToggleChip extends JToggleButton {
        private float selectedProgress;
        private float selectFrom;
        private float selectTo;
        private long selectStarted;
        private Timer selectTimer;

        public ToggleChip(String text) {
            super(text);
            setFont(Theme.SMALL);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorder(new EmptyBorder(5, 10, 5, 10));
            setOpaque(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            getModel().addChangeListener(e -> animateSelection());
        }

        private void animateSelection() {
            float target = isSelected() ? 1f : 0f;
            if (Math.abs(selectedProgress - target) < 0.001f) {
                return;
            }
            selectFrom = selectedProgress;
            selectTo = target;
            selectStarted = System.currentTimeMillis();
            if (selectTimer == null) {
                selectTimer = new Timer(16, e -> {
                    float elapsed = (System.currentTimeMillis() - selectStarted) / 160f;
                    float t = Math.min(1f, elapsed);
                    float eased = 1f - (1f - t) * (1f - t);
                    selectedProgress = selectFrom + (selectTo - selectFrom) * eased;
                    repaint();
                    if (t >= 1f) {
                        ((Timer) e.getSource()).stop();
                    }
                });
            }
            selectTimer.restart();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prepare(g);
            float p = selectedProgress;
            g2.setColor(Theme.mix(Theme.CARD_ALT, Theme.SEL, p));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g2.setColor(Theme.mix(Theme.BORDER, Theme.alpha(Theme.ACCENT, 120), p));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
            g2.setColor(Theme.mix(Theme.BORDER, Theme.ACCENT, p));
            g2.drawOval(9, getHeight() / 2 - 3, 6, 6);
            if (p > 0.02f) {
                g2.setColor(Theme.alpha(Theme.ACCENT, Math.round(255 * p)));
                g2.fillOval(10, getHeight() / 2 - 2, 4, 4);
            }
            g2.setFont(getFont());
            FontMetrics metrics = g2.getFontMetrics();
            String text = fitText(g2, getText(), getWidth() - 24);
            int x = 21;
            g2.setColor(Theme.mix(Theme.MUTED, Theme.TEXT, p));
            if (!text.isEmpty()) {
                g2.drawString(text, x,
                        (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
            }
            g2.dispose();
        }
    }

    public static final class ThinProgress extends JComponent {
        private double value;
        private boolean indeterminate;
        private long phase = System.currentTimeMillis();
        private final Timer timer;

        public ThinProgress() {
            setPreferredSize(new Dimension(100, 3));
            setMinimumSize(new Dimension(40, 3));
            timer = new Timer(30, e -> {
                if (indeterminate && isShowing()) {
                    repaint();
                }
            });
        }

        @Override
        public void addNotify() {
            super.addNotify();
            if (indeterminate) {
                timer.start();
            }
        }

        @Override
        public void removeNotify() {
            timer.stop();
            super.removeNotify();
        }

        public void setValue(double value) {
            this.value = Math.max(0, Math.min(1, value));
            repaint();
        }

        public void setIndeterminate(boolean indeterminate) {
            this.indeterminate = indeterminate;
            if (indeterminate && isShowing()) {
                timer.start();
            } else if (!indeterminate) {
                timer.stop();
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prepare(g);
            int width = getWidth();
            int height = getHeight();
            g2.setColor(Theme.CARD_ALT);
            g2.fillRoundRect(0, 0, width, height, height, height);
            g2.setColor(Theme.ACCENT);
            if (indeterminate) {
                int segment = Math.max(40, width / 4);
                double t = ((System.currentTimeMillis() - phase) % 1600) / 1600.0;
                int x = (int) ((width + segment) * t) - segment;
                g2.fillRoundRect(x, 0, segment, height, height, height);
            } else if (value > 0) {
                int filled = Math.max(height, (int) Math.round(width * value));
                g2.fillRoundRect(0, 0, filled, height, height, height);
            }
            g2.dispose();
        }
    }

    public static final class ThinScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = Theme.BG_4;
            trackColor = Theme.BG;
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private JButton zeroButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            button.setBorder(BorderFactory.createEmptyBorder());
            return button;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle trackBounds) {
            g.setColor(Theme.BG);
            g.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, java.awt.Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g2 = prepare(g);
            g2.setColor(thumbColor);
            g2.fillRoundRect(thumbBounds.x + 2, thumbBounds.y + 2,
                    Math.max(1, thumbBounds.width - 4), Math.max(1, thumbBounds.height - 4), 6, 6);
            g2.dispose();
        }

        @Override
        protected Dimension getMinimumThumbSize() {
            return new Dimension(6, 32);
        }
    }

    public static final class GrassIcon extends JComponent {
        private static final Color[][] PIXELS = new Color[10][10];

        static {
            int[][] grass = {
                    {1, 1, 2, 1, 2, 1, 1, 2, 1, 2},
                    {2, 1, 1, 2, 1, 1, 2, 1, 1, 2},
                    {1, 2, 1, 1, 2, 1, 1, 1, 2, 1},
                    {1, 1, 2, 1, 1, 2, 1, 2, 1, 1},
                    {3, 3, 1, 3, 1, 3, 3, 1, 3, 3},
                    {4, 3, 3, 4, 3, 3, 1, 3, 4, 3},
                    {3, 4, 3, 3, 4, 3, 3, 4, 3, 3},
                    {4, 3, 4, 3, 3, 4, 3, 3, 4, 3},
                    {3, 3, 4, 3, 3, 3, 4, 3, 3, 4},
                    {4, 3, 3, 4, 3, 3, 3, 4, 3, 3}
            };
            Color[] palette = {
                    null,
                    new Color(0xD8D8D8),
                    new Color(0xAFAFAF),
                    new Color(0x5A5A5A),
                    new Color(0x3F3F3F)
            };
            for (int row = 0; row < 10; row++) {
                for (int col = 0; col < 10; col++) {
                    PIXELS[row][col] = palette[grass[row][col]];
                }
            }
        }

        public GrassIcon(int size) {
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prepare(g);
            int size = Math.min(getWidth(), getHeight());
            int cell = Math.max(1, size / 10);
            int offsetX = (getWidth() - cell * 10) / 2;
            int offsetY = (getHeight() - cell * 10) / 2;
            g2.setColor(new Color(0x050505));
            g2.fillRoundRect(offsetX - 2, offsetY + 2, cell * 10 + 4, cell * 10 + 4, 5, 5);
            for (int row = 0; row < 10; row++) {
                for (int col = 0; col < 10; col++) {
                    g2.setColor(PIXELS[row][col]);
                    g2.fillRect(offsetX + col * cell, offsetY + row * cell, cell, cell);
                }
            }
            g2.setColor(new Color(255, 255, 255, 22));
            g2.fillRect(offsetX, offsetY, cell * 10, Math.max(1, cell / 2));
            g2.dispose();
        }
    }

    public static final class PlayerFace extends JComponent {
        private static final Color[][] PIXELS = new Color[8][8];

        static {
            Color hair = new Color(0x2A2A2A);
            Color hairLight = new Color(0x3D3D3D);
            Color skin = new Color(0xC4C4C4);
            Color skinLight = new Color(0xD7D7D7);
            Color skinDark = new Color(0xABABAB);
            Color eye = new Color(0xF2F2F2);
            Color iris = new Color(0x888888);
            Color mouth = new Color(0x555555);
            Color shirt = new Color(0x6A6A6A);
            Color[][] rows = {
                    {hair, hair, hair, hair, hair, hair, hair, hair},
                    {hair, hairLight, hair, hair, hair, hair, hairLight, hair},
                    {hair, skin, skin, skin, skin, skin, skin, hair},
                    {hair, skin, eye, skin, skin, eye, skin, hair},
                    {hair, skin, eye, skin, skin, eye, skin, hair},
                    {hair, skin, skin, skinLight, skinLight, skin, skinDark, hair},
                    {hair, skin, skin, mouth, mouth, skin, skin, hair},
                    {shirt, shirt, skin, skin, skin, skin, shirt, shirt}
            };
            for (int row = 0; row < 8; row++) {
                System.arraycopy(rows[row], 0, PIXELS[row], 0, 8);
            }
        }

        public PlayerFace(int size) {
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prepare(g);
            g2.setColor(Theme.CARD_ALT);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 7, 7);
            int size = Math.min(getWidth(), getHeight()) - 8;
            int cell = Math.max(1, size / 8);
            int offsetX = (getWidth() - cell * 8) / 2;
            int offsetY = (getHeight() - cell * 8) / 2;
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    g2.setColor(PIXELS[row][col]);
                    g2.fillRect(offsetX + col * cell, offsetY + row * cell, cell, cell);
                }
            }
            g2.dispose();
        }
    }

    public static final class SkinHead extends JComponent {
        private java.awt.Image skin;

        public SkinHead(int size) {
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
        }

        public void setSkin(java.awt.Image skin) {
            this.skin = skin;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prepare(g);
            int size = Math.min(getWidth(), getHeight());
            g2.setColor(Theme.CARD_ALT);
            g2.fillRoundRect(0, 0, size, size, 10, 10);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.setClip(new RoundRectangle2D.Float(0, 0, size, size, 10, 10));
            if (skin == null || skin.getWidth(null) < 16) {
                drawFallback(g2, size);
            } else {
                int pad = Math.max(1, size / 18);
                int inner = size - pad * 2;
                g2.drawImage(skin, pad, pad, pad + inner, pad + inner,
                        8, 8, 16, 16, null);
                if (skin.getWidth(null) >= 64 && skin.getHeight(null) >= 16) {
                    g2.setComposite(java.awt.AlphaComposite.getInstance(
                            java.awt.AlphaComposite.SRC_OVER, 0.9f));
                    g2.drawImage(skin, pad, pad, pad + inner, pad + inner,
                            40, 8, 48, 16, null);
                }
            }
            g2.setClip(null);
            g2.dispose();
        }

        private static void drawFallback(Graphics2D g2, int size) {
            int cell = Math.max(1, size / 8);
            int ox = (size - cell * 8) / 2;
            int oy = (size - cell * 8) / 2;
            Color hair = new Color(0x2A2A2A);
            Color skin = new Color(0xC4C4C4);
            Color eye = new Color(0xF2F2F2);
            Color mouth = new Color(0x555555);
            Color[][] rows = {
                    {hair, hair, hair, hair, hair, hair, hair, hair},
                    {hair, hair, hair, hair, hair, hair, hair, hair},
                    {hair, skin, skin, skin, skin, skin, skin, hair},
                    {hair, skin, eye, skin, skin, eye, skin, hair},
                    {hair, skin, eye, skin, skin, eye, skin, hair},
                    {hair, skin, skin, skin, skin, skin, skin, hair},
                    {hair, skin, skin, mouth, mouth, skin, skin, hair},
                    {hair, skin, skin, skin, skin, skin, skin, hair}
            };
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    g2.setColor(rows[row][col]);
                    g2.fillRect(ox + col * cell, oy + row * cell, cell, cell);
                }
            }
        }
    }

    public static final class ProjectIcon extends JComponent {
        private String url;
        private java.awt.Image image;
        private int shimmerPhase;
        private long shimmerStarted = System.currentTimeMillis();
        private Timer shimmer;

        public ProjectIcon(int size) {
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
            shimmer = new Timer(45, e -> {
                if (image != null || ImageCache.get(url) != null) {
                    ((Timer) e.getSource()).stop();
                    return;
                }
                if (System.currentTimeMillis() - shimmerStarted > 15_000) {
                    ((Timer) e.getSource()).stop();
                    return;
                }
                shimmerPhase = (shimmerPhase + 7) % 140;
                repaint();
            });
        }

        public void setUrl(String url) {
            if (java.util.Objects.equals(this.url, url)) {
                return;
            }
            this.url = url;
            this.image = ImageCache.get(url);
            shimmerStarted = System.currentTimeMillis();
            if (image == null && url != null) {
                ImageCache.load(url, () -> {
                    image = ImageCache.get(url);
                    shimmer.stop();
                    repaint();
                });
                shimmer.start();
            } else {
                shimmer.stop();
            }
            repaint();
        }

        @Override
        public void addNotify() {
            super.addNotify();
            if (image == null && ImageCache.get(url) == null && url != null) {
                shimmer.start();
            }
        }

        @Override
        public void removeNotify() {
            shimmer.stop();
            super.removeNotify();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prepare(g);
            int size = Math.min(getWidth(), getHeight());
            g2.setColor(Theme.CARD_ALT);
            g2.fillRoundRect(0, 0, size, size, 12, 12);
            java.awt.Image current = image != null ? image : ImageCache.get(url);
            if (current != null) {
                g2.setClip(new RoundRectangle2D.Float(0, 0, size, size, 12, 12));
                double scale = Math.max(size / (double) current.getWidth(null),
                        size / (double) current.getHeight(null));
                int w = (int) Math.ceil(current.getWidth(null) * scale);
                int h = (int) Math.ceil(current.getHeight(null) * scale);
                g2.drawImage(current, (size - w) / 2, (size - h) / 2, w, h, null);
                g2.setClip(null);
            } else {
                int s = Math.max(10, size / 3);
                int cx = size / 2;
                int cy = size / 2;
                java.awt.Polygon top = new java.awt.Polygon(
                        new int[]{cx, cx + s / 2, cx, cx - s / 2},
                        new int[]{cy - s, cy - s / 2, cy, cy - s / 2}, 4);
                java.awt.Polygon left = new java.awt.Polygon(
                        new int[]{cx - s / 2, cx, cx, cx - s / 2},
                        new int[]{cy - s / 2, cy, cy + s, cy + s / 2}, 4);
                java.awt.Polygon right = new java.awt.Polygon(
                        new int[]{cx + s / 2, cx, cx, cx + s / 2},
                        new int[]{cy - s / 2, cy, cy + s, cy + s / 2}, 4);
                g2.setColor(Theme.DIM);
                g2.fill(top);
                g2.setColor(Theme.alpha(Theme.DIM, 190));
                g2.fill(left);
                g2.setColor(Theme.alpha(Theme.DIM, 140));
                g2.fill(right);
                g2.setClip(new RoundRectangle2D.Float(0, 0, size, size, 12, 12));
                int band = (int) ((shimmerPhase / 140.0) * (size + 46)) - 24;
                g2.setPaint(new GradientPaint(band, 0, Theme.alpha(Theme.ACCENT, 0),
                        band + 22, 0, Theme.alpha(Theme.ACCENT, 42)));
                g2.fillRect(0, 0, size, size);
                g2.setClip(null);
            }
            g2.dispose();
        }
    }

    public static final class LogoMark extends JComponent {
        public LogoMark(int size) {
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prepare(g);
            int size = Math.min(getWidth(), getHeight());
            int arc = Math.max(6, size / 4);
            g2.setPaint(new GradientPaint(0, 0, Theme.ACCENT, size, size, Theme.ACCENT_2));
            g2.fillRoundRect(0, 0, size, size, arc, arc);

            double cx = size / 2.0;
            double cy = size / 2.0;
            double s = size * 0.56;
            java.awt.Polygon top = new java.awt.Polygon(
                    new int[]{(int) cx, (int) (cx + s * 0.55), (int) cx, (int) (cx - s * 0.55)},
                    new int[]{(int) (cy - s * 0.58), (int) (cy - s * 0.18),
                            (int) (cy + s * 0.22), (int) (cy - s * 0.18)}, 4);
            java.awt.Polygon left = new java.awt.Polygon(
                    new int[]{(int) (cx - s * 0.55), (int) cx, (int) cx, (int) (cx - s * 0.55)},
                    new int[]{(int) (cy - s * 0.18), (int) (cy + s * 0.22),
                            (int) (cy + s * 0.72), (int) (cy + s * 0.32)}, 4);
            java.awt.Polygon right = new java.awt.Polygon(
                    new int[]{(int) (cx + s * 0.55), (int) cx, (int) cx, (int) (cx + s * 0.55)},
                    new int[]{(int) (cy - s * 0.18), (int) (cy + s * 0.22),
                            (int) (cy + s * 0.72), (int) (cy + s * 0.32)}, 4);
            g2.setColor(Theme.ACCENT_TEXT);
            g2.fill(top);
            g2.setColor(Theme.alpha(Theme.ACCENT_TEXT, 220));
            g2.fill(left);
            g2.setColor(Theme.alpha(Theme.ACCENT_TEXT, 185));
            g2.fill(right);
            g2.dispose();
        }
    }

    public static final class GradientHeader extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = prepare(g);
            g2.setPaint(new GradientPaint(0, 0, new Color(0x111111), getWidth(), 0,
                    new Color(0x0A0A0A)));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(new Color(0x2A2A2A));
            g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            g2.dispose();
        }
    }
}
