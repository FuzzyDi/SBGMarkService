package uz.sbg.kmreplacement.overlay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.sbg.kmreplacement.state.CorrelationKey;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Фасад для работы с overlay-окнами. По одному окну на пару
 * {@link CorrelationKey} + {@code attemptIndex}. Потокобезопасен.
 *
 * <p>Разные {@code attemptIndex} в пределах одного базового ключа — это
 * разные позиции в чеке (например, 4 одинаковых кока-колы, каждая со своим
 * индивидуальным КМ). Для каждой позиции, требующей замены, поднимается
 * отдельное окно.</p>
 *
 * <p>Если одновременно активны несколько окон — специального позиционирования
 * не делаем (все в одной точке, одно поверх другого). На MVP достаточно.</p>
 */
public final class QrOverlayService {

    private static final Logger log = LoggerFactory.getLogger(QrOverlayService.class);

    private final Map<String, QrOverlayWindow> windows =
            new ConcurrentHashMap<String, QrOverlayWindow>();

    private static String mkKey(CorrelationKey key, int attemptIndex) {
        return (key == null ? "-" : key.asString()) + "#" + attemptIndex;
    }

    public void show(CorrelationKey key, int attemptIndex, BufferedImage qr, String title, String subtitle) {
        if (key == null) return;
        String mk = mkKey(key, attemptIndex);
        QrOverlayWindow w = windows.get(mk);
        if (w == null) {
            w = new QrOverlayWindow();
            QrOverlayWindow prev = windows.putIfAbsent(mk, w);
            if (prev != null) w = prev;
        }
        w.show(qr, title, subtitle);
        log.info("[SBG-KMR] overlay SHOW requested | key={}", mk);
    }

    public void hide(CorrelationKey key, int attemptIndex) {
        if (key == null) return;
        String mk = mkKey(key, attemptIndex);
        QrOverlayWindow w = windows.remove(mk);
        if (w != null) {
            w.hide();
            log.info("[SBG-KMR] overlay HIDE requested | key={}", mk);
        }
    }

    public boolean isShown(CorrelationKey key, int attemptIndex) {
        if (key == null) return false;
        QrOverlayWindow w = windows.get(mkKey(key, attemptIndex));
        return w != null && w.isShown();
    }

    public void hideAll() {
        List<String> keys = new ArrayList<String>(windows.keySet());
        for (String mk : keys) {
            QrOverlayWindow w = windows.remove(mk);
            if (w != null) {
                w.hide();
                log.info("[SBG-KMR] overlay HIDE requested | key={}", mk);
            }
        }
    }

    public int active() {
        return windows.size();
    }
}
