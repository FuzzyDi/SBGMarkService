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
 * Фасад для работы с overlay-окнами. По одному окну на
 * {@link CorrelationKey}. Потокобезопасен.
 *
 * <p>Решение "одно окно на correlationKey" важно: в чеке может быть
 * несколько товаров с КМ, и по каждому может висеть своя замена. Но на
 * практике одновременно их почти никогда не бывает, т.к. кассир обычно
 * разбирается с одним КМ до следующего.</p>
 *
 * <p>Если одновременно активны несколько окон — мы не делаем
 * специального позиционирования (все показываются в одной точке, один
 * поверх другого). На MVP этого достаточно. Улучшение — см. R5 в
 * проектном документе (очередь показа или раскладка стэком).</p>
 */
public final class QrOverlayService {

    private static final Logger log = LoggerFactory.getLogger(QrOverlayService.class);

    private final Map<CorrelationKey, QrOverlayWindow> windows =
            new ConcurrentHashMap<CorrelationKey, QrOverlayWindow>();

    public void show(CorrelationKey key, BufferedImage qr, String title, String subtitle) {
        if (key == null) return;
        QrOverlayWindow w = windows.get(key);
        if (w == null) {
            w = new QrOverlayWindow();
            QrOverlayWindow prev = windows.putIfAbsent(key, w);
            if (prev != null) w = prev;
        }
        w.show(qr, title, subtitle);
        log.info("[SBG-KMR] overlay SHOW requested | key={}", key);
    }

    public void hide(CorrelationKey key) {
        if (key == null) return;
        QrOverlayWindow w = windows.remove(key);
        if (w != null) {
            w.hide();
            log.info("[SBG-KMR] overlay HIDE requested | key={}", key);
        }
    }

    public boolean isShown(CorrelationKey key) {
        if (key == null) return false;
        QrOverlayWindow w = windows.get(key);
        return w != null && w.isShown();
    }

    public void hideAll() {
        List<CorrelationKey> keys = new ArrayList<CorrelationKey>(windows.keySet());
        for (CorrelationKey k : keys) {
            hide(k);
        }
    }

    public int active() {
        return windows.size();
    }
}
