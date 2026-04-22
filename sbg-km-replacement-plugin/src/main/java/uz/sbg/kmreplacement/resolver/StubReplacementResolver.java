package uz.sbg.kmreplacement.resolver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stub-резолвер для стендовой проверки UX-потока без реального бекенда.
 *
 * <p><b>Формат генерируемого заменяющего КМ (GS1 DataMatrix-совместимый):</b></p>
 * <pre>
 *   01 &lt;14-digit GTIN&gt; 21 &lt;13-char serial, содержит {@value #VALID_MARKER}&gt; &lt;FNC1&gt; 93 &lt;4-char CRC&gt;
 * </pre>
 *
 * <p>FNC1 ({@code U+001D}, ASCII GS) — обязательный разделитель между
 * переменной AI 21 и следующей AI 93. Без него SR10 парсит код как
 * «Некорректная марка» ещё до валидационного плагина.</p>
 *
 * <p>Детекция валидной (ранее выданной нами) марки: строка содержит
 * {@value #VALID_MARKER}. Маркер короткий и ASCII-only — чтобы помещался
 * в AI 21 (макс. 20 символов переменной длины) и не ломал GS1-парсер.</p>
 *
 * <p>Этот резолвер НЕ ходит в сеть и нужен только для прохождения
 * UX-сценария скан A → overlay с заменой B → скан B → ACCEPT.
 * После подтверждения UX stub заменяется на {@code HttpReplacementResolver},
 * который ходит в {@code sbg-marking-server-py}.</p>
 */
public final class StubReplacementResolver implements ReplacementResolver {

    private static final Logger log = LoggerFactory.getLogger(StubReplacementResolver.class);

    /** Маркер в AI 21 serial, по которому узнаём наш же сгенерированный КМ. */
    public static final String VALID_MARKER = "SBGOK";     // 5 ASCII букв, influids в AI 21
    /** ASCII GS / FNC1 — разделитель между переменными AI. */
    public static final char   FNC1         = 0x1D;
    /** Дефолтный GTIN для fallback-сценария, когда ctx.gtin пуст. */
    public static final String FALLBACK_GTIN = "04780069000130";

    @Override
    public ResolveOutcome resolve(String scannedKm, ResolveContext ctx) {
        if (scannedKm == null || scannedKm.isEmpty()) {
            return ResolveOutcome.unavailable("empty KM");
        }
        if (scannedKm.contains(VALID_MARKER)) {
            log.info("[SBG-KMR-STUB] VALID | km='{}'", abbreviate(scannedKm));
            return ResolveOutcome.valid();
        }

        String gtin = (ctx != null && ctx.getGtin() != null && ctx.getGtin().length() == 14)
                ? ctx.getGtin()
                : FALLBACK_GTIN;

        // serial = "SBGOK" + 8 последних цифр timestamp → 13 ASCII-символов.
        long ts = System.currentTimeMillis();
        String tsTail = Long.toString(ts);
        if (tsTail.length() > 8) tsTail = tsTail.substring(tsTail.length() - 8);
        while (tsTail.length() < 8) tsTail = "0" + tsTail;
        String serial = VALID_MARKER + tsTail;   // 5+8 = 13

        // Псевдо-CRC AI 93 — фиксированный 4-символьный суффикс. Для stub'а
        // проверка CRC не производится, реальному бекенду он не передаётся.
        String crc = "KMST";

        String replacement = "01" + gtin + "21" + serial + FNC1 + "93" + crc;

        log.info("[SBG-KMR-STUB] REPLACE | scanned='{}' -> replacement='{}' (len={})",
                abbreviate(scannedKm), abbreviate(replacement.replace(FNC1, '^')), replacement.length());
        return ResolveOutcome.replaceWith(replacement);
    }

    private static String abbreviate(String km) {
        if (km == null) return "null";
        if (km.length() <= 40) return km;
        return km.substring(0, 37) + "...";
    }
}
