package uz.sbg.kmreplacement.overlay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Конкретное Swing-окно, отображающее QR + подпись.
 *
 * <p>Все изменения UI делаются ТОЛЬКО в Swing EDT через
 * {@link SwingUtilities#invokeLater(Runnable)}. Обращаться к
 * {@code show()/hide()} можно из любого потока — синхронизация
 * обеспечена внутри.</p>
 *
 * <p>Параметры окна:</p>
 * <ul>
 *   <li>{@link JWindow} без рамки (чтобы не крал фокус и не показывал
 *       стандартную кнопку закрытия);</li>
 *   <li>{@code setAlwaysOnTop(true)} — поверх всего UI кассы;</li>
 *   <li>{@code setFocusableWindowState(false)} + {@code setAutoRequestFocus(false)}
 *       — не перехватывает фокус у кассового UI;</li>
 *   <li>позиция — правый верхний угол (см. {@link OverlayPlacement}).</li>
 * </ul>
 */
public final class QrOverlayWindow {

    private static final Logger log = LoggerFactory.getLogger(QrOverlayWindow.class);

    public  static final int QR_PX    = 300;
    private static final int PADDING  = 16;
    private static final int TITLE_H  = 30;
    private static final int SUB_H    = 22;

    private volatile JWindow window;
    private Timer keepAliveTimer;   // только для EDT

    /** Показать (или обновить) overlay. Можно вызывать из любого потока. */
    public void show(final BufferedImage qr, final String title, final String subtitle) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    if (window == null) {
                        window = build();
                    }
                    updateContent(qr, title, subtitle);
                    positionAndShow();
                } catch (Throwable t) {
                    log.error("[SBG-KMR] overlay SHOW FAILED: {}", t.getMessage(), t);
                }
            }
        });
    }

    /** Скрыть overlay. Можно вызывать из любого потока. */
    public void hide() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    if (keepAliveTimer != null) {
                        keepAliveTimer.stop();
                        keepAliveTimer = null;
                    }
                    JWindow w = window;
                    if (w != null) {
                        w.setVisible(false);
                        w.dispose();
                        window = null;
                        log.info("[SBG-KMR] overlay HIDDEN");
                    }
                } catch (Throwable t) {
                    log.error("[SBG-KMR] overlay HIDE FAILED: {}", t.getMessage(), t);
                }
            }
        });
    }

    /** true — если окно сейчас видимо (вызов можно делать из любого потока). */
    public boolean isShown() {
        JWindow w = window;
        return w != null && w.isVisible();
    }

    // =========================================================================
    // Swing-private (вызывается только из EDT)
    // =========================================================================
    private JWindow build() {
        JWindow w = new JWindow();
        w.setAlwaysOnTop(true);
        w.setFocusableWindowState(false);
        w.setAutoRequestFocus(false);

        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setBackground(Color.WHITE);
        root.setBorder(new LineBorder(new Color(0x21, 0x21, 0x21), 2));
        root.setName("sbg-kmr-root");

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0xFF, 0xC1, 0x07));
        header.setBorder(new EmptyBorder(6, 12, 6, 12));
        JLabel title = new JLabel("", JLabel.CENTER);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        title.setForeground(new Color(0x21, 0x21, 0x21));
        title.setName("sbg-kmr-title");
        header.add(title, BorderLayout.CENTER);

        JLabel qr = new JLabel("", JLabel.CENTER);
        qr.setName("sbg-kmr-qr");
        qr.setBorder(new EmptyBorder(PADDING, PADDING, PADDING, PADDING));

        JLabel sub = new JLabel("", JLabel.CENTER);
        sub.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        sub.setForeground(new Color(0x42, 0x42, 0x42));
        sub.setBorder(new EmptyBorder(0, 8, 10, 8));
        sub.setName("sbg-kmr-sub");

        root.add(header, BorderLayout.NORTH);
        root.add(qr, BorderLayout.CENTER);
        root.add(sub, BorderLayout.SOUTH);
        w.setContentPane(root);
        return w;
    }

    private void updateContent(BufferedImage qr, String title, String subtitle) {
        JPanel root = (JPanel) window.getContentPane();
        findLabel(root, "sbg-kmr-title").setText(title);
        findLabel(root, "sbg-kmr-sub").setText(subtitle);
        JLabel qrLabel = findLabel(root, "sbg-kmr-qr");
        qrLabel.setIcon(qr != null ? new ImageIcon(qr) : null);
    }

    private static JLabel findLabel(java.awt.Container root, String name) {
        for (java.awt.Component c : root.getComponents()) {
            if (name.equals(c.getName()) && c instanceof JLabel) {
                return (JLabel) c;
            }
            if (c instanceof java.awt.Container) {
                JLabel l = findLabel((java.awt.Container) c, name);
                if (l != null) return l;
            }
        }
        return null;
    }

    private void positionAndShow() {
        int width  = QR_PX + 2 * PADDING + 4;
        int height = TITLE_H + QR_PX + 2 * PADDING + SUB_H + 4;
        window.setSize(new Dimension(width, height));

        Rectangle bounds = OverlayPlacement.screenBounds();
        int[] xy = OverlayPlacement.topRight(bounds, width, height);
        window.setLocation(xy[0], xy[1]);

        window.setVisible(true);
        window.toFront();

        // SR10-модалки (JDialog/JFrame alwaysOnTop) после закрытия перехватывают
        // z-order, из-за чего наш overlay уходит вниз. Периодически
        // переобъявляем topmost и toFront(), пока окно видимо.
        if (keepAliveTimer == null) {
            keepAliveTimer = new Timer(500, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    JWindow w = window;
                    if (w == null || !w.isVisible()) return;
                    try {
                        w.setAlwaysOnTop(false);
                        w.setAlwaysOnTop(true);
                        w.toFront();
                    } catch (Throwable ignore) { /* не ронять таймер */ }
                }
            });
            keepAliveTimer.setRepeats(true);
            keepAliveTimer.start();
        }

        log.info("[SBG-KMR] overlay SHOWN | pos=({},{}) | size={}x{} | screen={}",
                xy[0], xy[1], width, height, bounds);
    }
}
