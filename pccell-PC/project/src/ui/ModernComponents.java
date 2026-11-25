package ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Componentes Swing minimalistas alinhados ao sistema visual do PCcell.
 */
public final class ModernComponents {

    public static final Color BG_CANVAS = new Color(9, 9, 11);
    public static final Color BG_SURFACE = new Color(17, 17, 20);
    public static final Color BG_ELEVATED = new Color(24, 24, 28);
    public static final Color BORDER_SUBTLE = new Color(39, 39, 42);
    public static final Color BORDER_HOVER = new Color(63, 63, 70);
    public static final Color TEXT_PRIMARY = new Color(250, 250, 250);
    public static final Color TEXT_SECONDARY = new Color(161, 161, 170);
    public static final Color TEXT_MUTED = new Color(113, 113, 122);
    public static final Color ACCENT_BLUE = new Color(37, 99, 235);
    public static final Color ACCENT_BLUE_HOVER = new Color(59, 130, 246);
    public static final Color ACCENT_CYAN = new Color(56, 189, 248);
    public static final Color STATUS_SUCCESS = new Color(16, 185, 129);
    public static final Color STATUS_WARNING = new Color(245, 158, 11);
    public static final Color STATUS_ERROR = new Color(239, 68, 68);

    private ModernComponents() {}

    public static Font uiFont(int style, int size) {
        return new Font("Segoe UI", style, size);
    }

    public static Font monoFont(int style, int size) {
        return new Font("Consolas", style, size);
    }

    /**
     * Painel com cantos arredondados e borda hairline sutil.
     */
    public static class RoundedPanel extends JPanel {
        private final int radius;
        private Color fillColor;
        private Color strokeColor;

        public RoundedPanel(int radius, Color fillColor, Color strokeColor) {
            this.radius = radius;
            this.fillColor = fillColor;
            this.strokeColor = strokeColor;
            setOpaque(false);
        }

        public void setFillColor(Color c) {
            this.fillColor = c;
            repaint();
        }

        public void setStrokeColor(Color c) {
            this.strokeColor = c;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            Shape round = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1f, h - 1f, radius, radius);
            if (fillColor != null) {
                g2.setColor(fillColor);
                g2.fill(round);
            }
            if (strokeColor != null) {
                g2.setColor(strokeColor);
                g2.draw(round);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Botao minimalista com cantos arredondados, hover suave e sem estilo padrao do SO.
     */
    public static class ModernButton extends JButton {
        private final boolean primary;
        private final int radius;
        private boolean hovered = false;
        private boolean pressed = false;

        public ModernButton(String text, boolean primary) {
            this(text, primary, 10);
        }

        public ModernButton(String text, boolean primary, int radius) {
            super(text);
            this.primary = primary;
            this.radius = radius;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(uiFont(Font.BOLD, 12));
            setForeground(primary ? Color.WHITE : TEXT_PRIMARY);
            setBorder(new EmptyBorder(8, 14, 8, 14));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hovered = false; pressed = false; repaint(); }
                @Override public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
                @Override public void mouseReleased(MouseEvent e) { pressed = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color bg;
            Color stroke = null;
            if (!isEnabled()) {
                bg = primary ? new Color(37, 99, 235, 80) : new Color(24, 24, 28, 140);
                stroke = primary ? null : new Color(39, 39, 42, 140);
            } else if (primary) {
                bg = pressed ? new Color(29, 78, 216) : (hovered ? ACCENT_BLUE_HOVER : ACCENT_BLUE);
            } else {
                bg = pressed ? new Color(32, 32, 38) : (hovered ? new Color(30, 30, 36) : BG_ELEVATED);
                stroke = hovered ? BORDER_HOVER : BORDER_SUBTLE;
            }

            Shape shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, radius, radius);
            g2.setColor(bg);
            g2.fill(shape);
            if (stroke != null) {
                g2.setColor(stroke);
                g2.draw(shape);
            }

            if (!isEnabled()) {
                setForeground(primary ? new Color(255, 255, 255, 130) : TEXT_MUTED);
            } else {
                setForeground(primary ? Color.WHITE : TEXT_PRIMARY);
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /**
     * Campo de texto escuro com borda arredondada e placeholder clean.
     */
    public static class ModernTextField extends JTextField {
        private final String placeholder;
        private boolean focused = false;

        public ModernTextField(String placeholder) {
            this.placeholder = placeholder;
            setOpaque(false);
            setFont(uiFont(Font.PLAIN, 13));
            setForeground(TEXT_PRIMARY);
            setCaretColor(TEXT_PRIMARY);
            setBorder(new EmptyBorder(10, 14, 10, 14));
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusGained(java.awt.event.FocusEvent e) { focused = true; repaint(); }
                @Override public void focusLost(java.awt.event.FocusEvent e) { focused = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape shape = new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1f, getHeight() - 1f, 18, 18);
            g2.setColor(isEnabled() ? BG_CANVAS : new Color(13, 13, 16));
            g2.fill(shape);
            g2.setColor(focused ? ACCENT_BLUE : BORDER_SUBTLE);
            g2.draw(shape);
            g2.dispose();
            super.paintComponent(g);

            if (getText().isEmpty() && placeholder != null) {
                Graphics2D gp = (Graphics2D) g.create();
                gp.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                gp.setColor(TEXT_MUTED);
                gp.setFont(getFont());
                FontMetrics fm = gp.getFontMetrics();
                int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
                gp.drawString(placeholder, 15, y);
                gp.dispose();
            }
        }
    }

    /**
     * Scrollbar minimalista escura.
     */
    public static void styleScrollBar(JScrollPane scrollPane) {
        JScrollBar bar = scrollPane.getVerticalScrollBar();
        bar.setUnitIncrement(16);
        bar.setPreferredSize(new Dimension(7, 0));
        bar.setBackground(BG_CANVAS);
        bar.setUI(new BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor = new Color(48, 48, 54);
                trackColor = BG_CANVAS;
            }
            @Override protected JButton createDecreaseButton(int orientation) { return zeroButton(); }
            @Override protected JButton createIncreaseButton(int orientation) { return zeroButton(); }
            private JButton zeroButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                return b;
            }
            @Override protected void paintThumb(Graphics g, JComponent c, Rectangle r) {
                if (r.isEmpty() || !scrollbar.isEnabled()) return;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isDragging ? BORDER_HOVER : new Color(48, 48, 56));
                g2.fillRoundRect(r.x + 1, r.y + 2, r.width - 2, r.height - 4, 6, 6);
                g2.dispose();
            }
            @Override protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
                g.setColor(BG_CANVAS);
                g.fillRect(r.x, r.y, r.width, r.height);
            }
        });
    }

    /**
     * Desenha icone minimalista de PC + Smartphone para janela e tray.
     */
    public static Image createAppIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Fundo escuro arredondado
        g.setColor(BG_SURFACE);
        g.fillRoundRect(1, 1, size - 2, size - 2, size / 4, size / 4);
        g.setColor(BORDER_SUBTLE);
        g.drawRoundRect(1, 1, size - 3, size - 3, size / 4, size / 4);

        float s = size / 32f;
        g.setStroke(new BasicStroke(Math.max(1.2f, 2f * s), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Monitor
        g.setColor(ACCENT_CYAN);
        g.drawRoundRect(Math.round(5 * s), Math.round(7 * s), Math.round(16 * s), Math.round(11 * s), Math.round(2 * s), Math.round(2 * s));
        g.drawLine(Math.round(13 * s), Math.round(18 * s), Math.round(13 * s), Math.round(22 * s));
        g.drawLine(Math.round(9 * s), Math.round(22 * s), Math.round(17 * s), Math.round(22 * s));

        // Celular sobreposto
        g.setColor(BG_SURFACE);
        g.fillRoundRect(Math.round(18 * s), Math.round(11 * s), Math.round(9 * s), Math.round(14 * s), Math.round(2.5f * s), Math.round(2.5f * s));
        g.setColor(STATUS_SUCCESS);
        g.drawRoundRect(Math.round(18 * s), Math.round(11 * s), Math.round(9 * s), Math.round(14 * s), Math.round(2.5f * s), Math.round(2.5f * s));

        g.dispose();
        return img;
    }
}
