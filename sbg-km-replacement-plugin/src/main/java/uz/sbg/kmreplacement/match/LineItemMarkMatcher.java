package uz.sbg.kmreplacement.match;

import ru.crystals.pos.spi.receipt.LineItem;
import ru.crystals.pos.spi.receipt.MarkInfo;
import ru.crystals.pos.spi.receipt.Receipt;
import uz.sbg.kmreplacement.util.Strings;

import java.util.List;

/**
 * Утилита сопоставления нашего внутреннего {@code replacementKm} (полный
 * GS1 DataMatrix payload) с {@link LineItem} в {@link Receipt} на момент
 * фискализации.
 *
 * <p>Стратегия сопоставления (в порядке убывания надёжности):</p>
 * <ol>
 *   <li>Прямое сравнение строк: {@code markInfo.getMarkCode() == ourKm}.</li>
 *   <li>Fallback по {@code getExcise()} — некоторые версии SR10 сохраняют КМ
 *       именно там для маркированных позиций.</li>
 *   <li>Fallback по GTIN+серийнику, выдранным из нашего payload вида
 *       {@code 01<14 цифр GTIN>21<serial>...}.</li>
 * </ol>
 *
 * <p>Почему fallbacks нужны: SR10 может нормализовать отсканированный
 * DataMatrix (например, вырезать криптохвост или поменять представление FNC1).
 * Строковое сравнение может сорваться из-за одного символа.</p>
 */
public final class LineItemMarkMatcher {

    private LineItemMarkMatcher() { /* util */ }

    /**
     * Найти LineItem в чеке, соответствующий нашему replacementKm. Возвращает
     * null, если совпадений нет (вероятно, позиция удалена до фискализации).
     */
    public static LineItem findMatching(Receipt receipt, String ourKm) {
        if (receipt == null || Strings.isBlank(ourKm)) return null;
        List<LineItem> items;
        try {
            items = receipt.getLineItems();
        } catch (Throwable ignore) {
            return null;
        }
        if (items == null || items.isEmpty()) return null;

        String ourGtin = extractGtin(ourKm);
        String ourSerial = extractSerial(ourKm);

        for (LineItem li : items) {
            if (li == null) continue;

            // 1) Прямое совпадение по markCode
            MarkInfo mi = safeMarkInfo(li);
            if (mi != null) {
                String mc = mi.getMarkCode();
                if (mc != null && mc.equals(ourKm)) {
                    return li;
                }
            }

            // 2) Excise fallback
            String excise = safeExcise(li);
            if (excise != null && excise.equals(ourKm)) {
                return li;
            }

            // 3) GTIN + serial fallback
            if (mi != null && ourGtin != null && ourSerial != null) {
                String gtin = mi.getGtin();
                String serial = mi.getSerialNumber();
                if (ourGtin.equals(gtin) && ourSerial.equals(serial)) {
                    return li;
                }
            }
        }
        return null;
    }

    private static MarkInfo safeMarkInfo(LineItem li) {
        try { return li.getMarkInfo(); } catch (Throwable t) { return null; }
    }

    private static String safeExcise(LineItem li) {
        try { return li.getExcise(); } catch (Throwable t) { return null; }
    }

    /**
     * Выдёргивает GTIN (14 цифр после AI "01") из GS1-payload.
     * Возвращает null, если не получилось.
     */
    static String extractGtin(String km) {
        if (km == null || km.length() < 16) return null;
        if (!km.startsWith("01")) return null;
        String gtin = km.substring(2, 16);
        for (int i = 0; i < gtin.length(); i++) {
            if (!Character.isDigit(gtin.charAt(i))) return null;
        }
        return gtin;
    }

    /**
     * Выдёргивает серийник (AI "21") до первого FNC1/GS. Возвращает null,
     * если структура не распознана.
     */
    static String extractSerial(String km) {
        if (km == null) return null;
        int ai21 = km.indexOf("21", 16);    // серийник следует за 16-символьным "01<GTIN>"
        if (ai21 != 16) return null;
        int start = ai21 + 2;
        if (start >= km.length()) return null;
        // Серийник может быть до 20 символов AI 21 по GS1; идёт до FNC1 (0x1D) или до конца.
        int end = km.length();
        for (int i = start; i < km.length(); i++) {
            char c = km.charAt(i);
            if (c == 0x1D) { end = i; break; }
        }
        // Ограничиваем разумной длиной, чтобы не хватать остаток payload, если FNC1 нет.
        if (end - start > 20) end = start + 20;
        return km.substring(start, end);
    }
}
