package uz.sbg.kmreplacement.overlay;

// ВАЖНО: импорт идёт из исходного пакета com.google.zxing.
// maven-shade-plugin на этапе package ПЕРЕМЕЩАЕТ байткодовые ссылки
// на uz.sbg.kmreplacement.shaded.zxing.* — компилятору знать об этом
// не нужно.
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.datamatrix.DataMatrixWriter;
import com.google.zxing.datamatrix.encoder.SymbolShapeHint;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * Построитель 2D-кода для overlay.
 *
 * <p><b>Формат — DataMatrix GS1, не QR.</b> Причина: SR10 сначала
 * парсит отсканированный код как акцизный GS1 DataMatrix (с FNC1 на
 * стыке переменных AI), и только затем передаёт строку в
 * {@code ExciseValidationPlugin}. QR-код с плоской строкой
 * {@code 01<gtin>21...} отбивается кассой как «Некорректная марка»
 * ещё до вызова плагина, а значит сценарий "скан QR с экрана" просто
 * не срабатывает.</p>
 *
 * <p>Параметры:</p>
 * <ul>
 *   <li>{@link EncodeHintType#GS1_FORMAT} = true — ZXing вставит
 *       FNC1 в начало кодового слова и интерпретирует символ
 *       {@code U+001D} (GS) во входной строке как FNC1-разделитель;</li>
 *   <li>{@link SymbolShapeHint#FORCE_SQUARE} — квадратный символ (удобнее
 *       сканировать 2D-ридером);</li>
 *   <li>кодировка UTF-8, но реальный КМ — ASCII.</li>
 * </ul>
 *
 * <p>Имя класса исторически — {@code QrPayloadBuilder}; на данный момент
 * это DataMatrix. Переименовать можно отдельным рефакторингом, чтобы
 * не ломать импорты в оверлейном модуле.</p>
 *
 * <p>ZXing в сборке перемещён через maven-shade в
 * {@code uz.sbg.kmreplacement.shaded.zxing} — чтобы не конфликтовать
 * с другими плагинами SR10, содержащими иную версию ZXing.</p>
 */
public final class QrPayloadBuilder {

    /** ASCII GS / FNC1-разделитель в GS1-DataMatrix. */
    public static final char FNC1 = 0x1D;

    private QrPayloadBuilder() {}

    public static BufferedImage build(String payload, int sizePx) throws WriterException {
        if (payload == null) throw new IllegalArgumentException("payload is null");
        if (sizePx <= 0) throw new IllegalArgumentException("sizePx <= 0");

        Map<EncodeHintType, Object> hints = new EnumMap<EncodeHintType, Object>(EncodeHintType.class);
        hints.put(EncodeHintType.GS1_FORMAT, Boolean.TRUE);
        hints.put(EncodeHintType.DATA_MATRIX_SHAPE, SymbolShapeHint.FORCE_SQUARE);
        hints.put(EncodeHintType.MARGIN, 2);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        BitMatrix matrix = new DataMatrixWriter()
                .encode(payload, BarcodeFormat.DATA_MATRIX, sizePx, sizePx, hints);

        int w = matrix.getWidth();
        int h = matrix.getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int black = Color.BLACK.getRGB();
        int white = Color.WHITE.getRGB();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, matrix.get(x, y) ? black : white);
            }
        }
        return img;
    }
}
