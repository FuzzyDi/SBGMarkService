package uz.sbg.kmreplacement.overlay;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;

/**
 * Выбор экрана и позиции для overlay.
 *
 * <p>Стратегия: если есть активное окно кассы — берём его
 * {@code GraphicsConfiguration}; иначе — default screen; в крайнем
 * случае {@code Toolkit.getScreenSize()}.</p>
 *
 * <p>Позиция: правый верхний угол с отступом. На стенде SR10 10.2.82
 * (экран 958×576 в VNC) подтверждено, что правый верхний угол не
 * перекрывает критический UI — ни списка позиций чека, ни модалки
 * "Отсканируйте марку".</p>
 */
public final class OverlayPlacement {

    private OverlayPlacement() {}

    public static final int MARGIN_PX = 24;

    public static Rectangle screenBounds() {
        try {
            Window focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
            if (focused != null && focused.getGraphicsConfiguration() != null) {
                return focused.getGraphicsConfiguration().getBounds();
            }
            return GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice()
                    .getDefaultConfiguration()
                    .getBounds();
        } catch (Throwable t) {
            Dimension sz = Toolkit.getDefaultToolkit().getScreenSize();
            return new Rectangle(0, 0, sz.width, sz.height);
        }
    }

    public static int[] topRight(Rectangle screen, int widthPx, int heightPx) {
        int x = screen.x + screen.width - widthPx - MARGIN_PX;
        int y = screen.y + MARGIN_PX;
        return new int[] { x, y };
    }
}
